package com.are.distribuidora.domain.pedido.model

/**
 * Parámetros para crear un pedido en el repositorio.
 * Permite que el repositorio genere el ID y maneje la transacción.
 */
data class CreatePedidoParams(
    val vendedorId: String,
    val routeId: String,
    /** Fecha de entrega en formato "YYYY-MM-DD". Requerida para la subida a Firestore. */
    val deliveryDate: String,
    val clienteId: String?, // Nullable para clientes temporales
    val clienteSnapshot: ClienteSnapshot,
    val items: List<CreatePedidoItemInput>,
    val descuentoGlobal: Double
)
