package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.repository.RouteRepository

class AssignClientToRouteUseCase(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(clientId: String, routeId: String): Result<Unit> =
        repository.assignClientToRoute(clientId = clientId, routeId = routeId)
}
