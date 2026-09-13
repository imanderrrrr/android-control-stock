package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Sube a Firestore las ediciones locales pendientes de pedidos (pendingUpload=1),
 * preservando el dueño original. Lo invoca [OrdersUploadWorker].
 */
class UploadPendingOrdersUseCase(
    private val repository: OrderRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.uploadPendingOrders()
}
