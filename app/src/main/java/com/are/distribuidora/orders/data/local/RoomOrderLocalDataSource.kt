package com.are.distribuidora.orders.data.local

import android.util.Log
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import kotlinx.coroutines.flow.Flow

class RoomOrderLocalDataSource(
    private val db: DistribuidoraDatabase,
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val stagingDao: OrderItemStagingDao,
    /** 4.1: libro de movimientos + contador local de stock (null solo en tests legacy). */
    private val movementDao: com.are.distribuidora.stockmovement.data.local.dao.StockMovementDao? = null,
    private val productDao: com.are.distribuidora.data.local.dao.ProductDao? = null,
) : OrderLocalDataSource {

    private val tag = "OrdersSync"

    override suspend fun upsertOrderHeader(entity: OrderEntity) {
        orderDao.upsert(entity)
    }

    override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {
        orderDao.deleteOwnHeaders(routeId = routeId, deliveryDate = deliveryDate, vendedorId = vendedorId, now = now)
    }

    override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
        orderDao.deleteAllOwnHeadersByRoute(routeId = routeId, vendedorId = vendedorId, now = now)
    }

    override suspend fun markOrderDeleted(orderId: String, now: Long) {
        orderDao.markDeleted(orderId = orderId, now = now)
    }

    override suspend fun deleteItemsByOrderId(orderId: String) {
        orderItemDao.deleteByOrderId(orderId)
        stagingDao.deleteByOrderId(orderId)
    }

    override suspend fun getOrderById(orderId: String): OrderEntity? =
        orderDao.getById(orderId)

    override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<OrderEntity> =
        orderDao.getByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate)

    override suspend fun markInProgress(orderId: String, now: Long) {
        val current = orderDao.getById(orderId) ?: return

        // Caso 5: app cerrada durante sync / restos inconsistentes.
        // Antes de iniciar un nuevo intento, limpiamos staging huérfano para este pedido.
        try {
            stagingDao.deleteByOrderId(orderId)
        } catch (e: Exception) {
            Log.w(tag, "markInProgress: no se pudo limpiar staging (orderId=$orderId): ${e.message}")
        }

        orderDao.upsert(
            current.copy(
                downloadStatus = "IN_PROGRESS",
                failedReasonCode = null,
                failedReasonMessage = null,
                // failedAttempts NO se incrementa aquí: solo se cuenta si realmente falla.
                // El incremento ocurre en markFailed para no penalizar intentos exitosos.
                lastAttemptAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun markFailed(
        orderId: String,
        now: Long,
        reasonCode: String,
        reasonMessage: String,
    ) {
        val current = orderDao.getById(orderId) ?: return
        orderDao.upsert(
            current.copy(
                downloadStatus = "FAILED",
                failedReasonCode = reasonCode,
                failedReasonMessage = reasonMessage,
                // failedAttempts se incrementa aquí, donde sí se confirmó el fallo.
                failedAttempts = current.failedAttempts + 1,
                lastAttemptAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun clearStaging(orderId: String) {
        stagingDao.deleteByOrderId(orderId)
    }

    override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
        stagingDao.insertAll(items)
    }

    override suspend fun countStaging(orderId: String): Int = stagingDao.countByOrderId(orderId)

    override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> = stagingDao.getByOrderId(orderId)

    override suspend fun commitItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        itemsDownloaded: Int,
        now: Long,
    ) {
        if (finalItems.isEmpty()) {
            // Nunca marcar COMPLETED sin items (evita corrupción/descarga parcial).
            throw IllegalArgumentException("commitItems: finalItems vacío")
        }

        db.runInTransaction {
            // 1) Reemplazo total (evitar duplicados)
            orderItemDao.deleteByOrderId(orderId)
            orderItemDao.insertAll(finalItems)

            // 2) Limpiar staging
            stagingDao.deleteByOrderId(orderId)

            // 3) Actualizar cabecera
            val current = orderDao.getById(orderId) ?: throw IllegalStateException("Order not found")
            val updated = current.copy(
                totalAmount = totalAmount,
                itemsDownloaded = itemsDownloaded,
                downloadStatus = "COMPLETED",
                failedReasonCode = null,
                failedReasonMessage = null,
                updatedAt = now,
            )
            orderDao.upsert(updated)
        }
    }

    override suspend fun commitEditedItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        now: Long,
    ) {
        if (finalItems.isEmpty()) {
            // Un pedido editado debe conservar al menos un ítem. Para "vaciarlo" se usa el
            // flujo de eliminación (deleteOrder), no la edición.
            throw IllegalArgumentException("commitEditedItems: finalItems vacío")
        }

        db.runInTransaction {
            // 1) Reemplazo total de ítems (PK estable = itemId; índice único orderId+productId).
            orderItemDao.deleteByOrderId(orderId)
            orderItemDao.insertAll(finalItems)

            // 2) Limpiar cualquier staging residual del flujo de descarga.
            stagingDao.deleteByOrderId(orderId)

            // 3) Actualizar cabecera SIN tocar vendedorId/sellerName (preserva al dueño).
            val current = orderDao.getById(orderId) ?: throw IllegalStateException("Order not found")
            val updated = current.copy(
                itemsCount = finalItems.size,
                itemsDownloaded = finalItems.size,
                totalAmount = totalAmount,
                downloadStatus = "COMPLETED",
                failedReasonCode = null,
                failedReasonMessage = null,
                failedAttempts = 0,
                pendingUpload = true,
                updatedAt = now,
            )
            orderDao.upsert(updated)
        }
    }

    override suspend fun commitEditedItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        now: Long,
        movements: List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity>,
    ) {
        if (finalItems.isEmpty()) throw IllegalArgumentException("commitEditedItems: finalItems vacío")
        db.runInTransaction {
            orderItemDao.deleteByOrderId(orderId)
            orderItemDao.insertAll(finalItems)
            stagingDao.deleteByOrderId(orderId)
            val current = orderDao.getById(orderId) ?: throw IllegalStateException("Order not found")
            orderDao.upsert(
                current.copy(
                    itemsCount = finalItems.size,
                    itemsDownloaded = finalItems.size,
                    totalAmount = totalAmount,
                    downloadStatus = "COMPLETED",
                    failedReasonCode = null,
                    failedReasonMessage = null,
                    failedAttempts = 0,
                    pendingUpload = true,
                    editVersion = current.editVersion + 1,
                    updatedAt = now,
                )
            )
            // Movimientos por diferencia + stock local, en la misma transacción que la edición.
            recordMovements(movements)
        }
    }

    override suspend fun commitOrderDeletion(
        orderId: String,
        now: Long,
        syncedMovements: List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity>,
    ) {
        db.runInTransaction {
            orderDao.markDeleted(orderId = orderId, now = now)
            recordMovements(syncedMovements)
        }
    }

    /** Inserta (idempotente por id) y aplica al stock local solo los que realmente se insertaron. */
    private suspend fun recordMovements(movements: List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity>) {
        val mDao = movementDao ?: return
        val pDao = productDao ?: return
        movements.forEach { m ->
            if (mDao.insert(m) != -1L) pDao.applyMovement(m.productId, m.signedQuantity)
        }
    }

    override suspend fun getUnsyncedMovements(orderId: String) = movementDao?.getUnsyncedByOrderId(orderId) ?: emptyList()
    override suspend fun markMovementsSyncing(ids: List<String>) { if (ids.isNotEmpty()) movementDao?.markSyncing(ids) }
    override suspend fun markMovementsSynced(ids: List<String>, at: Long) { if (ids.isNotEmpty()) movementDao?.markSynced(ids, at) }
    override suspend fun revertMovementsSyncing(ids: List<String>) { if (ids.isNotEmpty()) movementDao?.revertSyncingToPending(ids) }

    override suspend fun getPendingUploadOrders(): List<OrderEntity> = orderDao.getPendingUpload()

    override suspend fun setPendingUpload(orderId: String, pending: Boolean, now: Long) {
        orderDao.setPendingUpload(orderId = orderId, pending = pending, now = now)
    }

    override fun observeByRoute(routeId: String): Flow<List<OrderEntity>> =
        orderDao.observeByRoute(routeId)

    override fun observeByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<OrderEntity>> =
        orderDao.observeByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate)

    override suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity> =
        orderItemDao.getByOrderId(orderId)
}
