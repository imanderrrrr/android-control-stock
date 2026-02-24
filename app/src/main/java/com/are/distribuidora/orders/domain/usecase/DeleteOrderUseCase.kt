package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Caso de uso: soft delete de un pedido.
 *
 * Delega en [OrderRepository.deleteOrder]:
 *  1) Marca isDeleted=true en Firestore.
 *  2) Marca isDeleted=true localmente en Room.
 *  3) Elimina items locales (final + staging).
 */
class DeleteOrderUseCase(
    private val repository: OrderRepository,
) {
    suspend fun execute(routeId: String, orderId: String): Result<Unit> =
        repository.deleteOrder(routeId = routeId, orderId = orderId)
}

