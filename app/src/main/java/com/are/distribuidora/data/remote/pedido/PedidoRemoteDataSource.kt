package com.are.distribuidora.data.remote.pedido

/**
 * Contrato de subida de pedidos al backend remoto (Firestore).
 * No conoce Room ni SyncStatus — solo trabaja con datos de negocio.
 */
interface PedidoRemoteDataSource {

    /**
     * Sube un pedido completo (encabezado + ítems) de forma atómica usando WriteBatch.
     *
     * @param pedidoId   ID del pedido (usado como nombre del documento).
     * @param payload    Datos del encabezado del pedido.
     * @param items      Lista de ítems del pedido.
     */
    suspend fun uploadPedido(
        pedidoId: String,
        payload: PedidoPayload,
        items: List<PedidoItemPayload>
    )

    /**
     * Soft delete en Firestore: marca isDeleted=true en el documento del pedido.
     * NO borra físicamente. Inactiva el lock si se provee orderKey.
     *
     * @param routeId    ID de la ruta del pedido.
     * @param pedidoId   ID del pedido.
     * @param orderKey   Clave de negocio para inactivar el lock (puede ser null).
     * @param deletedByUid  UID del usuario que elimina.
     */
    suspend fun softDeletePedido(
        routeId: String,
        pedidoId: String,
        orderKey: String?,
        deletedByUid: String?,
    )

    /**
     * Marca el pedido en Firestore como expirado (isDeleted=true + expiredAt).
     * Fase 1 del borrado por antigüedad: el pedido queda invisible para la app
     * pero sigue en Firestore durante [EXPIRE_GRACE_DAYS] días para auditoría.
     *
     * @param routeId  ID de la ruta del pedido.
     * @param pedidoId ID del pedido a marcar.
     */
    suspend fun markPedidoExpiredInCloud(
        routeId: String,
        pedidoId: String,
    )

    /**
     * Borra físicamente el pedido y todos sus ítems de Firestore (hard delete).
     * Fase 2 del borrado por antigüedad: se ejecuta cuando el pedido ya lleva
     * [EXPIRE_GRACE_DAYS] días marcado como expirado en la nube.
     *
     * Usa WriteBatch para eliminar el documento raíz y todos sus sub-documentos
     * en una sola operación atómica.
     *
     * @param routeId  ID de la ruta del pedido.
     * @param pedidoId ID del pedido a eliminar físicamente.
     */
    suspend fun hardDeletePedidoFromCloud(
        routeId: String,
        pedidoId: String,
    )

    companion object {
        /** Días de gracia en nube antes del hard delete (fase 2). */
        const val EXPIRE_GRACE_DAYS = 2L
        /** Antigüedad en días para considerar un pedido expirado. */
        const val EXPIRE_THRESHOLD_DAYS = 14L
    }

    data class PedidoPayload(
        val vendedorId: String,
        val routeId: String,
        /** Fecha de entrega en formato "YYYY-MM-DD". Requerida para que Firestore pueda filtrar por fecha al descargar. */
        val deliveryDate: String,
        val clienteId: String?,
        val clienteNombre: String,
        val clienteTelefono: String?,
        val clienteDireccion: String?,
        val subtotal: Double,
        val descuentoGlobal: Double,
        val total: Double,
        val version: Int,
        val actualizadoPor: String,
        val creadoEn: Long,
        val actualizadoEn: Long,
        /**
         * Clave determinística de negocio. Null para clientes temporales.
         * SHA-256(routeId|deliveryDate|clientId|vendedorId) en hex lowercase.
         */
        val orderKey: String? = null,
    )

    data class PedidoItemPayload(
        val id: String,
        val productoId: String,
        val nombre: String,
        val precioUnitario: Double,
        val cantidad: Int,
        val descuentoItem: Double,
        val totalItem: Double,
        val notes: String? = null,
    )
}

