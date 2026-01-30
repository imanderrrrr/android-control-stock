package com.are.distribuidora.orders.data.local

import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity

interface OrderLocalDataSource {
    suspend fun upsertOrderHeader(entity: OrderEntity)
    suspend fun getOrderById(orderId: String): OrderEntity?
    suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<OrderEntity>

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
}
