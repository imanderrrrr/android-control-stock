package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.repository.ClientRepository
import javax.inject.Inject

class DeleteClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(id: String) {
        clientRepository.delete(id)
    }
}
