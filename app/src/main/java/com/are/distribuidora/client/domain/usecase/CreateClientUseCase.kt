package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result

class CreateClientUseCase(
    private val repository: ClientRepository,
) {
    suspend operator fun invoke(client: Client): Result<Unit> = repository.create(client)
}
