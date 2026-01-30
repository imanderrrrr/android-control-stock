package com.are.distribuidora.orders.domain.model

data class OrderItem(
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
) {
    val lineTotal: Double = unitPrice * quantity
}
