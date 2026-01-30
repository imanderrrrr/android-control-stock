package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.repository.RouteRepository

class UpdateRouteDeliveryDayUseCase(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(routeId: String, deliveryDay: Int): Result<Unit> =
        repository.updateDeliveryDay(routeId = routeId, deliveryDay = deliveryDay)
}
