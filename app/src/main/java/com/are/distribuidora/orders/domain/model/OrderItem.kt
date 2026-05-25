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
) {
    val lineTotal: Double = unitPrice * quantity
}
