package com.are.distribuidora.client.domain.validator

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import javax.inject.Inject

/**
 * Validador de dominio para la entidad Client.
 * Centraliza las reglas de integridad de datos antes de persistir.
 */
class ClientValidator @Inject constructor() {

    fun validate(client: Client): Result<Unit> {
        if (client.name.isBlank()) {
            return Result.Error(Failure.ValidationError("El nombre del cliente no puede estar vacío."))
        }

        if (client.routeId.isBlank()) {
            return Result.Error(Failure.ValidationError("El cliente debe estar asignado a una ruta."))
        }

        client.maxOrderAmountInCents?.let { limit ->
            if (limit < 0) {
                return Result.Error(Failure.ValidationError("El límite de crédito no puede ser negativo."))
            }
        }

        return Result.Success(Unit)
    }
}
