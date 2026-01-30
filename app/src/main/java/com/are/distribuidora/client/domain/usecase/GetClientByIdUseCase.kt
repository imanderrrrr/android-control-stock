package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result

class GetClientByIdUseCase(
    private val repository: ClientRepository,
) {
    suspend operator fun invoke(id: String): Result<Client?> = repository.getClientById(id)
}
