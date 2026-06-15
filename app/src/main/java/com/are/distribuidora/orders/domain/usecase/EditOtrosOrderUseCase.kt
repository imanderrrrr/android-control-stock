package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Edita los ítems de un pedido de "Otros Pedidos" (ajeno): agrega/quita/ajusta ítems,
 * incluyendo ítems personalizados. Persiste en Room y marca el pedido para subir a
 * Firestore PRESERVANDO al vendedor original (no se "roba" el pedido).
 *
 * La subida remota la dispara la capa de presentación encolando [OrdersUploadScheduler].
 */
class EditOtrosOrderUseCase(
    private val repository: OrderRepository,
) {
    suspend operator fun invoke(orderId: String, items: List<EditOrderItemInput>): Result<Unit> =
        repository.editOrderItems(orderId = orderId, items = items)
}
