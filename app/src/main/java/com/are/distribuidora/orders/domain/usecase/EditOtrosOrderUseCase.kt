package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.screenaccess.domain.repository.UserAccessProvider

/**
 * Edita los ítems de un pedido de "Otros Pedidos" (ajeno): agrega/quita/ajusta ítems,
 * incluyendo ítems personalizados. Persiste en Room y marca el pedido para subir a
 * Firestore PRESERVANDO al vendedor original (no se "roba" el pedido).
 *
 * Autorización (roles 4.0): exige EDIT_ANY_ORDER, o EDIT_OWN_ORDER cuando el pedido es
 * del usuario (`order.vendedorId == uid`). De lo contrario [Failure.Forbidden].
 *
 * La subida remota la dispara la capa de presentación encolando [OrdersUploadScheduler].
 */
class EditOtrosOrderUseCase(
    private val repository: OrderRepository,
    private val userAccessProvider: UserAccessProvider,
    private val currentUserIdProvider: CurrentUserIdProvider,
) {
    suspend operator fun invoke(orderId: String, items: List<EditOrderItemInput>): Result<Unit> {
        val forbidden = checkOrderEditAllowed(orderId, repository, userAccessProvider, currentUserIdProvider)
        if (forbidden != null) return forbidden
        return repository.editOrderItems(orderId = orderId, items = items)
    }
}

/**
 * Regla compartida por editar/borrar en el sistema `orders`: null si está permitido,
 * `Result.Error(Failure.Forbidden)` si no.
 */
internal suspend fun checkOrderEditAllowed(
    orderId: String,
    repository: OrderRepository,
    userAccessProvider: UserAccessProvider,
    currentUserIdProvider: CurrentUserIdProvider,
): Result.Error? {
    val access = userAccessProvider.current()
    if (access.can(Permission.EDIT_ANY_ORDER)) return null
    if (!access.can(Permission.EDIT_OWN_ORDER)) return Result.Error(Failure.Forbidden)
    val uid = currentUserIdProvider.get()
    val owner = repository.getOrderById(orderId)?.vendedorId
    if (uid.isNullOrBlank() || owner.isNullOrBlank() || owner != uid) return Result.Error(Failure.Forbidden)
    return null
}
