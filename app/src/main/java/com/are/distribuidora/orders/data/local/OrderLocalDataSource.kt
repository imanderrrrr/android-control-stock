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
