package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.repository.RouteRepository

class CreateRouteUseCase(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(route: Route): Result<Unit> = repository.create(route)
}
