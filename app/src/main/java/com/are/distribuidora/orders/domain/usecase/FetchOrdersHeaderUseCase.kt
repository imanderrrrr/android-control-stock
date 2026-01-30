package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository

class FetchOrdersHeaderUseCase(
    private val repository: OrderRepository,
) {
    suspend fun execute(routeId: String, deliveryDate: String): Result<Unit> =
        repository.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)
}
