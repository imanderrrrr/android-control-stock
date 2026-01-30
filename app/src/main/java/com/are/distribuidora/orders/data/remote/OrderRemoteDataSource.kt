package com.are.distribuidora.orders.data.remote

/**
 * Contrato remoto solo lectura para pedidos.
 *
 * Firestore NO maneja estados de descarga.
 */
interface OrderRemoteDataSource {
    data class OrderHeaderDto(
        val orderId: String,
        val routeId: String,
        val deliveryDate: String,
        val clientName: String,
        val clientAddress: String?,
        val sellerName: String?,
        val itemsCount: Int,
    )

    data class OrderItemDto(
        val productId: String,
        val productName: String,
        val unitPrice: Double,
        val quantity: Int,
    )

    /**
     * Busca pedidos por ruta+fecha y retorna solo cabeceras.
     */
    suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderHeaderDto>

    /**
     * Descarga items completos de un pedido.
     */
    suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderItemDto>
}
