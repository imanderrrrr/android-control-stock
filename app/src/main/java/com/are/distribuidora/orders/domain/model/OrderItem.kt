package com.are.distribuidora.orders.domain.model

import com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase

data class OrderItem(
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
) {
    /**
     * Total por línea redondeado al múltiplo de Q 0.25 más cercano.
     * Mantiene la coherencia con el total del pedido (también redondeado) y con las
     * denominaciones monetarias de Guatemala (0, 25, 50, 75 centavos).
     */
    val lineTotal: Double = RoundToQuarterQuetzalUseCase(unitPrice * quantity)
}
