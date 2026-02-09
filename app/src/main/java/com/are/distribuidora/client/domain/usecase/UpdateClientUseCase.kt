package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import javax.inject.Inject

/**
 * UseCase para editar un cliente existente.
 *
 * Reglas de Negocio:
 * 1. El cliente debe tener un ID válido.
 * 2. La persistencia es "Offline-First": se guarda localmente y se marca para sync.
 * 3. Estrategia de Conflicto: Last Write Wins (basado en updatedAt).
 */
class UpdateClientUseCase @Inject constructor(
    private val repository: ClientRepository
) {
    suspend operator fun invoke(client: Client): Result<Unit> {
        if (client.id.isBlank()) {
            return Result.Error(Failure.ValidationError("El ID del cliente no puede estar vacío"))
        }

        if (client.name.isBlank()) {
             return Result.Error(Failure.ValidationError("El nombre es obligatorio"))
        }

        // Delegar al repositorio para manejo de transacción y estado de sync
        return repository.update(client)
    }
}
