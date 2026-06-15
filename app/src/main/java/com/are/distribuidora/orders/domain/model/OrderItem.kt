package com.are.distribuidora.orders.domain.model

data class OrderItem(
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    /**
     * Detalle / instrucción especial del cliente para este ítem.
     * Snapshot inmutable descargado desde Firestore — esta pantalla es solo lectura.
     */
    val notes: String? = null,
    /**
     * docId estable del ítem en la subcolección de Firestore (mismo que OrderItemEntity.itemId).
     * Necesario para EDITAR el pedido preservando la identidad de cada ítem al subir.
     * "" si se desconoce (no debería ocurrir para ítems ya descargados).
     */
    val itemId: String = "",
) {
    val lineTotal: Double = unitPrice * quantity
}
