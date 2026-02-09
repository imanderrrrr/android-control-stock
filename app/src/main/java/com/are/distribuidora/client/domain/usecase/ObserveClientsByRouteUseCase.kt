package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveClientsByRouteUseCase @Inject constructor(
    private val repository: ClientRepository,
) {
    operator fun invoke(routeId: String): Flow<List<Client>> {
        return repository.observeByRouteId(routeId = routeId, limit = 100)
    }
}
