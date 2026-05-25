package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository

private const val TAG = "EXPIRE_PEDIDO"

/** Antigüedad mínima en días para considerar un pedido expirado. */
private const val EXPIRE_THRESHOLD_DAYS = 14L
/** Días de gracia en la nube antes del hard delete. */
private const val EXPIRE_GRACE_DAYS = 2L

/**
 * Caso de uso de dominio: expiración y borrado de pedidos antiguos.
 *
 * Reglas de negocio:
 *  - Un pedido con ≥ 14 días de antigüedad debe eliminarse.
 *  - Si estaba SYNCED en Firestore:
 *      · Fase 1 (día 14): marca `isDeleted=true` en Firestore.
 *      · Fase 2 (día 14 + 2 de gracia): hard delete físico en Firestore.
 *      En ambas fases se borra localmente de Room.
 *  - Si era PENDING_CREATE / FAILED (nunca subido): solo borra de Room.
 *
 * Sin conexión, los pedidos PENDING_CREATE se borran igualmente; los SYNCED
 * se posponen hasta el próximo ciclo con conectividad.
 */
class ExpirePedidosUseCase(
    private val repository: PedidoRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val logger: Logger,
) {
    suspend operator fun invoke(): Result<Unit> {
        logger.d(TAG, "ExpirePedidosUseCase started")
        logger.d(TAG, "Network available: ${connectivityChecker.isOnline()}")

        return repository.expireOldPedidos(
            thresholdDays = EXPIRE_THRESHOLD_DAYS,
            graceDays     = EXPIRE_GRACE_DAYS,
        ).also { result ->
            when (result) {
                is Result.Success -> logger.d(TAG, "ExpirePedidosUseCase finished OK")
                is Result.Error   -> logger.e(TAG, "ExpirePedidosUseCase finished with errors")
            }
        }
    }
}


