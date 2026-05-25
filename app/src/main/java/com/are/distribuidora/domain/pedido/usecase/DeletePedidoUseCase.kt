package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoRepository
import javax.inject.Inject

/**
 * Caso de uso: eliminar un pedido propio (soft delete consistente con productos/clientes).
 *
 * Comportamiento:
 * - Si sincronizado (SYNCED): marca isDeleted=true en Firestore + PENDING_DELETE local.
 * - Si pendiente (PENDING_CREATE/FAILED/etc.): borra físicamente de Room (nunca llegó a Firestore).
 * - Elimina items locales en ambos casos.
 */
class DeletePedidoUseCase @Inject constructor(
    private val repository: PedidoRepository,
) {
    suspend operator fun invoke(pedidoId: String): Result<Unit> =
        repository.deletePedido(pedidoId)
}

