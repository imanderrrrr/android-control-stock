package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.usecase.ValidateOrderLimitUseCase
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import javax.inject.Inject

/**
 * Caso de uso de dominio: editar un pedido propio existente.
 *
 * Pre-condiciones:
 * - [params.itemsToUpsert] contiene SOLO los ítems activos (el ViewModel ya filtró los eliminados).
 * - [params.itemIdsToDelete] contiene los IDs de ítems existentes que deben marcarse como isDeleted en Room.
 *
 * Validaciones:
 * - Al menos un ítem activo.
 * - Todas las cantidades > 0.
 * - Total resultante no negativo.
 * - Total no excede el límite de compra del cliente (si está configurado).
 */
class EditPedidoUseCase @Inject constructor(
    private val repository: PedidoRepository,
    private val clientRepository: ClientRepository,
    private val validateOrderLimitUseCase: ValidateOrderLimitUseCase,
) {
    suspend operator fun invoke(params: EditPedidoParams): Result<Unit> {
        if (params.itemsToUpsert.isEmpty()) {
            return Result.Error(Failure.ValidationError("El pedido debe tener al menos un ítem"))
        }
        if (params.itemsToUpsert.any { it.cantidad <= 0 }) {
            return Result.Error(Failure.ValidationError("La cantidad de cada ítem debe ser mayor a 0"))
        }
        val subtotal = params.itemsToUpsert.sumOf { (it.precioUnitario * it.cantidad) - it.descuentoItem }
        // Usar el total redondeado al Q 0.25, igual que lo que el repositorio guardará en BD
        val total = RoundToQuarterQuetzalUseCase(
            (subtotal - params.descuentoGlobal).coerceAtLeast(0.0)
        )
        if (total < 0) {
            return Result.Error(Failure.ValidationError("El total del pedido no puede ser negativo"))
        }

        // ── Validar límite de compra del cliente ──────────────────────────
        if (params.clienteId != null) {
            val clientResult = clientRepository.getClientById(params.clienteId)
            if (clientResult is Result.Success && clientResult.value != null) {
                // Validar contra el total redondeado, no el raw
                val totalInCents = (total * 100).toLong()
                val limitResult = validateOrderLimitUseCase(clientResult.value, totalInCents)
                if (limitResult is Result.Error) return limitResult
            }
        }
        // ──────────────────────────────────────────────────────────────────

        return repository.editPedido(params)
    }
}


