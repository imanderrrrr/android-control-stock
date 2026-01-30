package com.are.distribuidora.orders.domain.repository

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.model.Order

interface OrderRepository {
    /**
     * Descarga headers desde Firestore (solo lectura) y hace upsert en Room.
     *
     * Reglas:
     * - NO descarga items
     * - status queda en ITEMS_PENDING
     */
    suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String): Result<Unit>

    /**
     * Descarga items de UN pedido y hace commit atómico (staging -> final) solo si pasan validación.
     */
    suspend fun downloadOrderItems(orderId: String): Result<Unit>

    /**
     * Lectura local (fuente de verdad) para que la UI consuma más adelante.
     */
    suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<Order>
}
