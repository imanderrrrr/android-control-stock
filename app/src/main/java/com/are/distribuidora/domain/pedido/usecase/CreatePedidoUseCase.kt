package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.usecase.ValidateOrderLimitUseCase
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import javax.inject.Inject

class CreatePedidoUseCase @Inject constructor(
    private val repository: PedidoRepository,
    private val clientRepository: ClientRepository,
    private val validateOrderLimitUseCase: ValidateOrderLimitUseCase,
) {
    suspend operator fun invoke(
        vendedorId: String,
        routeId: String,
        deliveryDate: String = "",
        cliente: ClienteSelection,
        descuentoGlobal: Double,
        items: List<CreatePedidoItemInput>
    ): Result<String> {
        if (items.isEmpty()) {
            return Result.Error(Failure.ValidationError("El pedido debe tener al menos un ítem"))
        }

        if (items.any { it.cantidad <= 0 }) {
            return Result.Error(Failure.ValidationError("La cantidad de los ítems debe ser mayor a 0"))
        }

        val tempSubtotal = items.sumOf { (it.precioUnitario * it.cantidad) - it.descuentoItem }
        val tempTotal = tempSubtotal - descuentoGlobal
        val tempTotalRedondeado = RoundToQuarterQuetzalUseCase(tempTotal)

        if (tempTotal < 0) {
            return Result.Error(Failure.ValidationError("El total del pedido no puede ser negativo"))
        }

        val (clienteId, clienteSnapshot) = when (cliente) {
            is ClienteSelection.Existente -> {
                val result = clientRepository.getClientById(cliente.clienteId)
                if (result is Result.Error) return result

                val existingClient = (result as Result.Success).value
                    ?: return Result.Error(Failure.ValidationError("Cliente no encontrado con id ${cliente.clienteId}"))

                // ── Validar límite de compra con el total ya redondeado ───────
                val orderTotalInCents = (tempTotalRedondeado * 100).toLong()
                val limitResult = validateOrderLimitUseCase(existingClient, orderTotalInCents)
                if (limitResult is Result.Error) return limitResult
                // ──────────────────────────────────────────────────────────────

                val snapshot = ClienteSnapshot(
                    nombre = existingClient.name,
                    telefono = existingClient.phone,
                    direccion = existingClient.address
                )
                Pair(cliente.clienteId, snapshot)
            }
            is ClienteSelection.Temporal -> {
                if (cliente.snapshot.nombre.isBlank()) {
                    return Result.Error(Failure.ValidationError("El nombre del cliente temporal es obligatorio"))
                }
                Pair(null, cliente.snapshot)
            }
        }

        // ── Regla de negocio: 1 pedido por cliente por día de creación ────────
        // Solo aplica a clientes con ID estable (no temporales).
        // "Mismo día" se evalúa sobre System.currentTimeMillis() en zona local.
        // Esta regla es independiente del worker de expiración (14 días): aquel
        // limpia datos viejos del dispositivo; esta regla controla la creación.
        if (clienteId != null) {
            val now = System.currentTimeMillis()
            val existingId = repository.findActivePedidoByClienteAndDay(
                clienteId = clienteId,
                creationEpochMs = now,
            )
            if (existingId != null) {
                return Result.Error(Failure.DuplicateOrder(existingId))
            }
        }
        // ──────────────────────────────────────────────────────────────────

        val params = CreatePedidoParams(
            vendedorId = vendedorId,
            routeId = routeId,
            deliveryDate = deliveryDate,
            clienteId = clienteId,
            clienteSnapshot = clienteSnapshot,
            items = items,
            descuentoGlobal = descuentoGlobal,
            totalRedondeado = tempTotalRedondeado,
        )

        return repository.createPedido(params)
    }
}
