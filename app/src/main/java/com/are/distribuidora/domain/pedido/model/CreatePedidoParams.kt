package com.are.distribuidora.domain.pedido.model

/**
 * Parámetros para crear un pedido en el repositorio.
 * Permite que el repositorio genere el ID y maneje la transacción.
 *
 * [totalRedondeado] es el total final ya redondeado al Q 0.25 más cercano,
 * calculado por el UseCase de dominio. El repositorio lo persiste
 * directamente en lugar de recalcular, garantizando consistencia en toda la app.
 */
data class CreatePedidoParams(
    val vendedorId: String,
    val routeId: String,
    /** Fecha de entrega en formato "YYYY-MM-DD". Requerida para la subida a Firestore. */
    val deliveryDate: String,
    val clienteId: String?, // Nullable para clientes temporales
    val clienteSnapshot: ClienteSnapshot,
    val items: List<CreatePedidoItemInput>,
    val descuentoGlobal: Double,
    /** Total final redondeado al Q 0.25 más cercano. Calculado en el UseCase de dominio. */
    val totalRedondeado: Double,
)
