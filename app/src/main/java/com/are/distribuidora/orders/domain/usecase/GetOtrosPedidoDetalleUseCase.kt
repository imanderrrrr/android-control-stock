package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderItem
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Obtiene la cabecera y los ítems descargados de un pedido de "Otros Pedidos" desde Room.
 *
 * Devuelve null en el header si el pedido no existe o está eliminado.
 * Devuelve lista vacía de ítems si aún no se descargaron.
 */
class GetOtrosPedidoDetalleUseCase(
    private val repository: OrderRepository,
) {
    data class Result(
        val order: Order?,
        val items: List<OrderItem>,
    )

    suspend operator fun invoke(orderId: String): Result {
        val order = repository.getOrderById(orderId)
        val items = if (order != null) repository.getItemsByOrderId(orderId) else emptyList()
        return Result(order = order, items = items)
    }
}

