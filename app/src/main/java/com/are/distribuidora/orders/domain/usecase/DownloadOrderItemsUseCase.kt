package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository

class DownloadOrderItemsUseCase(
    private val repository: OrderRepository,
) {
    suspend fun execute(orderId: String): Result<Unit> =
        repository.downloadOrderItems(orderId = orderId)
}
