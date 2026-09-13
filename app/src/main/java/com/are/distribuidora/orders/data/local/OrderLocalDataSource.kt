package com.are.distribuidora.orders.data.local

import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import kotlinx.coroutines.flow.Flow

interface OrderLocalDataSource {
    suspend fun upsertOrderHeader(entity: OrderEntity)
    suspend fun getOrderById(orderId: String): OrderEntity?
    suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<OrderEntity>

    /**
     * Soft delete en Room de los headers propios acotados a routeId+deliveryDate+vendedorId.
     * Marca isDeleted=1 en lugar de borrar físicamente para preservar pedidos ya COMPLETED
     * con sus items descargados.
     */
    suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long)

    /**
     * Soft delete de todos los headers propios de una ruta (sin filtro de fecha).
     * Usado por fetchAllOrdersHeader para purgar pedidos propios antes de re-sincronizar.
     */
    suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long)

    /**
     * Soft delete local: marca isDeleted=1 en el header.
     * Equivalente al patrón de ProductDao.markAsPendingDelete / ClientDao.markDeleted.
     */
    suspend fun markOrderDeleted(orderId: String, now: Long)

    /**
     * Elimina los items finales y staging de un pedido.
     * Se llama tras un soft delete para que el detalle no quede accesible offline.
     */
    suspend fun deleteItemsByOrderId(orderId: String)

    suspend fun markInProgress(orderId: String, now: Long)
    suspend fun markFailed(
        orderId: String,
        now: Long,
        reasonCode: String,
        reasonMessage: String,
    )

    suspend fun clearStaging(orderId: String)
    suspend fun insertStaging(items: List<OrderItemStagingEntity>)
    suspend fun countStaging(orderId: String): Int
    suspend fun getStaging(orderId: String): List<OrderItemStagingEntity>

    /**
     * Commit atómico: borra items finales previos, inserta nuevos items finales,
     * limpia staging, y actualiza cabecera.
     */
    suspend fun commitItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        itemsDownloaded: Int,
        now: Long,
    )

    /**
     * Commit atómico de una EDICIÓN local de un pedido ajeno: reemplaza los ítems,
     * recalcula itemsCount/itemsDownloaded/totalAmount y marca pendingUpload=1 para
     * que el worker lo suba a Firestore. NO toca vendedorId/sellerName (el pedido
     * sigue siendo ajeno). downloadStatus queda COMPLETED (la edición es la verdad local).
     */
    suspend fun commitEditedItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        now: Long,
    )

    /**
     * 4.1: igual que [commitEditedItems] pero registrando en la MISMA transacción los movimientos
     * de stock por diferencia (PEDIDO_EDICION), aplicando su efecto al stock local e incrementando
     * `editVersion`. La implementación por defecto ignora los movimientos (fakes de tests).
     */
    suspend fun commitEditedItems(
        orderId: String,
        finalItems: List<OrderItemEntity>,
        totalAmount: Double,
        now: Long,
        movements: List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity>,
    ) = commitEditedItems(orderId, finalItems, totalAmount, now)

    /**
     * 4.1: soft delete local + movimientos compensatorios (PEDIDO_BORRADO) ya confirmados en el
     * servidor, con su efecto en el stock local. Atómico.
     */
    suspend fun commitOrderDeletion(
        orderId: String,
        now: Long,
        syncedMovements: List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity>,
    ) = markOrderDeleted(orderId, now)

    /** Movimientos del pedido aún no subidos (viajan con la edición). */
    suspend fun getUnsyncedMovements(orderId: String): List<com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity> = emptyList()
    suspend fun markMovementsSyncing(ids: List<String>) {}
    suspend fun markMovementsSynced(ids: List<String>, at: Long) {}
    suspend fun revertMovementsSyncing(ids: List<String>) {}

    /** Pedidos con ediciones locales pendientes de subir a Firestore. */
    suspend fun getPendingUploadOrders(): List<OrderEntity>

    /** Marca/limpia el flag pendingUpload de un pedido. */
    suspend fun setPendingUpload(orderId: String, pending: Boolean, now: Long)

    /**
     * Flow reactivo: emite la lista de Orders de una ruta cada vez que Room detecta cambios.
     * Excluye órdenes con isDeleted=true.
     */
    fun observeByRoute(routeId: String): Flow<List<OrderEntity>>

    /**
     * Flow reactivo filtrado por ruta Y fecha de entrega (YYYY-MM-DD).
     * Excluye eliminados.
     */
    fun observeByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<OrderEntity>>

    /**
     * Devuelve los ítems finales descargados para un pedido.
     */
    suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity>
}
