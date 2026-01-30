package com.are.distribuidora.orders.data.local

import android.util.Log
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity

class RoomOrderLocalDataSource(
    private val db: DistribuidoraDatabase,
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val stagingDao: OrderItemStagingDao,
) : OrderLocalDataSource {

    private val tag = "OrdersSync"

    override suspend fun upsertOrderHeader(entity: OrderEntity) {
        orderDao.upsert(entity)
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
                failedAttempts = current.failedAttempts + 1,
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
}
