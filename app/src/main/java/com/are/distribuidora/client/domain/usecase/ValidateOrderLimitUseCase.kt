package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import javax.inject.Inject

/**
 * Valida si el total del pedido excede el límite permitido del cliente.
 *
 * Regla de negocio:
 * Si client.maxOrderAmountInCents != null
 * Entonces orderTotalInCents <= client.maxOrderAmountInCents
 *
 * Si no se cumple, retorna Failure.ValidationError.
 */
class ValidateOrderLimitUseCase @Inject constructor() {

    operator fun invoke(
        client: Client,
        orderTotalInCents: Long
    ): Result<Unit> {

        val limit = client.maxOrderAmountInCents
            ?: return Result.Success(Unit)

        if (orderTotalInCents > limit) {

            val limitFormatted = limit / 100.0

            return Result.Error(
                Failure.ValidationError(
                    "El pedido excede el límite permitido de $$limitFormatted"
                )
            )
        }

        return Result.Success(Unit)
    }
}
