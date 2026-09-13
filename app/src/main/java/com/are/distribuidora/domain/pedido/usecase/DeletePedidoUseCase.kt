package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.screenaccess.domain.repository.UserAccessProvider
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Caso de uso: eliminar un pedido propio (soft delete consistente con productos/clientes).
 *
 * Autorización (roles 4.0): EDIT_ANY_ORDER borra cualquiera; si no, EDIT_OWN_ORDER exige que
 * `pedido.vendedorId` sea el uid de la sesión. De lo contrario [Failure.Forbidden].
 *
 * Comportamiento:
 * - Si sincronizado (SYNCED): marca isDeleted=true en Firestore + PENDING_DELETE local.
 * - Si pendiente (PENDING_CREATE/FAILED/etc.): borra físicamente de Room (nunca llegó a Firestore).
 * - Elimina items locales en ambos casos.
 */
class DeletePedidoUseCase @Inject constructor(
    private val repository: PedidoRepository,
    private val userAccessProvider: UserAccessProvider,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(pedidoId: String): Result<Unit> {
        val access = userAccessProvider.current()
        if (!access.can(Permission.EDIT_ANY_ORDER)) {
            if (!access.can(Permission.EDIT_OWN_ORDER)) return Result.Error(Failure.Forbidden)
            val uid = authRepository.getCurrentSession()?.userId
            val owner = repository.observePedidoWithItems(pedidoId).firstOrNull()?.pedido?.vendedorId
            if (uid.isNullOrBlank() || owner.isNullOrBlank() || owner != uid) {
                return Result.Error(Failure.Forbidden)
            }
        }
        return repository.deletePedido(pedidoId)
    }
}
