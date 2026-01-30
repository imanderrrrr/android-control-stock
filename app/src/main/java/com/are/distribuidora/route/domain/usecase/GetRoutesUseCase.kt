package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.repository.RouteRepository

class GetRoutesUseCase(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(limit: Int = 200): Result<List<Route>> = repository.getRoutes(limit)
}
