package com.are.distribuidora.domain.pedido.usecase

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
 */
class EditPedidoUseCase @Inject constructor(
    private val repository: PedidoRepository,
) {
    suspend operator fun invoke(params: EditPedidoParams): Result<Unit> {
        if (params.itemsToUpsert.isEmpty()) {
            return Result.Error(Failure.ValidationError("El pedido debe tener al menos un ítem"))
        }
        if (params.itemsToUpsert.any { it.cantidad <= 0 }) {
            return Result.Error(Failure.ValidationError("La cantidad de cada ítem debe ser mayor a 0"))
        }
        val subtotal = params.itemsToUpsert.sumOf { (it.precioUnitario * it.cantidad) - it.descuentoItem }
        val total    = subtotal - params.descuentoGlobal
        if (total < 0) {
            return Result.Error(Failure.ValidationError("El total del pedido no puede ser negativo"))
        }
        return repository.editPedido(params)
    }
}


