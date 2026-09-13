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
 * (PendingAccountFormFragment → PendingAccountEntity), esa cuenta entra aquí y
 * cualquier pantalla que muestre al cliente puede pintar su saldo/DEBE.
 *
 * Devuelve un mapa clientId → [ClientDebt] (solo clientes con deuda activa).
 */
class ObserveClientDebtsUseCase @Inject constructor(
    private val pendingAccountDao: PendingAccountDao,
) {
    operator fun invoke(): Flow<Map<String, ClientDebt>> =
        pendingAccountDao.observeAllActive(System.currentTimeMillis()).map { accounts ->
            // "Vencida" = la fecha límite es anterior a hoy (medianoche local). Es la misma
            // base que usa la lista de Cuentas Pendientes, para que "vence hoy" no salga vencida.
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            accounts
                .groupBy { it.clientId }
                .mapValues { (clientId, list) ->
                    ClientDebt(
                        clientId = clientId,
                        totalCents = list.sumOf { it.amountCents },
                        accountCount = list.size,
                        hasOverdue = list.any { it.dueDateMillis < todayStart },
                    )
                }
        }
}
