package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.screenaccess.domain.repository.UserAccessProvider

/**
 * Caso de uso: soft delete de un pedido del sistema `orders` (Otros Pedidos).
 *
 * Autorización (roles 4.0): EDIT_ANY_ORDER, o EDIT_OWN_ORDER si el pedido es del usuario.
 * De lo contrario `Failure.Forbidden`.
 *
 * Delega en [OrderRepository.deleteOrder]:
 *  1) Marca isDeleted=true en Firestore.
 *  2) Marca isDeleted=true localmente en Room.
 *  3) Elimina items locales (final + staging).
 */
class DeleteOrderUseCase(
    private val repository: OrderRepository,
    private val userAccessProvider: UserAccessProvider,
    private val currentUserIdProvider: CurrentUserIdProvider,
) {
    suspend fun execute(routeId: String, orderId: String): Result<Unit> {
        val forbidden = checkOrderEditAllowed(orderId, repository, userAccessProvider, currentUserIdProvider)
        if (forbidden != null) return forbidden
        return repository.deleteOrder(routeId = routeId, orderId = orderId)
    }
}
