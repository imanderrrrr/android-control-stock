package com.are.distribuidora.pendingaccount.domain.usecase

import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.pendingaccount.domain.model.ClientDebt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Observa la deuda agregada por cliente desde "cuentas por cobrar".
 *
 * Fuente única de verdad: cuando el vendedor registra que un cliente debe
 * (AddPendingAccountDialog → PendingAccountEntity), esa cuenta entra aquí y
 * cualquier pantalla que muestre al cliente puede pintar su saldo/DEBE.
 *
 * Devuelve un mapa clientId → [ClientDebt] (solo clientes con deuda activa).
 */
class ObserveClientDebtsUseCase @Inject constructor(
    private val pendingAccountDao: PendingAccountDao,
) {
    operator fun invoke(): Flow<Map<String, ClientDebt>> =
        pendingAccountDao.observeAllActive(System.currentTimeMillis()).map { accounts ->
            val now = System.currentTimeMillis()
            accounts
                .groupBy { it.clientId }
                .mapValues { (clientId, list) ->
                    ClientDebt(
                        clientId = clientId,
                        totalCents = list.sumOf { it.amountCents },
                        accountCount = list.size,
                        hasOverdue = list.any { it.dueDateMillis < now },
                    )
                }
        }
}
