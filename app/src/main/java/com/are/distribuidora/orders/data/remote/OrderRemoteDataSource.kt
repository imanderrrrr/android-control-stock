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
        /**
         * UID del vendedor que creó el pedido en Firestore.
         * Null si el campo no existe en el documento (pedidos legacy).
         * Regla Opción B: excluir si vendedorId == uid propio.
         * Si null/blank → se conserva (inclusión por defecto).
         */
        val vendedorId: String?,
        /**
         * Soft delete: si true, el pedido fue eliminado en Firestore.
         * El repositorio lo descarta y purga el local correspondiente.
         * Default false para compatibilidad con pedidos legacy sin el campo.
         */
        val isDeleted: Boolean = false,
    )

    data class OrderItemDto(
        /** Identificador estable del item — docId en Firestore subcolección items. */
        val itemId: String,
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
     * Busca TODOS los pedidos de una ruta sin filtrar por fecha.
     * Usado por "Otros Pedidos" para ver pedidos de todos los días.
     */
    suspend fun fetchAllOrderHeaders(routeId: String): List<OrderHeaderDto>

    /**
     * Descarga items completos de un pedido desde la subcolección:
     * routes/{routeId}/orders/{orderId}/items
     */
    suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderItemDto>

    /**
     * Soft delete en Firestore: actualiza isDeleted=true (+ updatedAt) en el doc del pedido.
     * NO borra físicamente el documento ni los items.
     * Consistente con el patrón de productos/clientes.
     *
     * @param deletedByUid  UID del usuario que realiza la eliminación (puede ser null si no hay sesión).
     */
    suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?)
}
