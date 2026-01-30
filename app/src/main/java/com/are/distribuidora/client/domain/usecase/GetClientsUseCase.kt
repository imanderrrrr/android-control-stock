package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result

class GetClientsUseCase(
    private val repository: ClientRepository,
) {
    suspend operator fun invoke(limit: Int = 50): Result<List<Client>> = repository.getClients(limit = limit)
}
