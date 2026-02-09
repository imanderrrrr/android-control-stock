package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result
import javax.inject.Inject

class GetClientsByRouteUseCase @Inject constructor(
    private val repository: ClientRepository,
) {
    suspend operator fun invoke(routeId: String): Result<List<Client>> {
        return repository.getByRouteId(routeId, 100)
    }
}
