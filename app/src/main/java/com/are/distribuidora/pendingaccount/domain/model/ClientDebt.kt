package com.are.distribuidora.pendingaccount.domain.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Deuda agregada de un cliente, derivada de "cuentas por cobrar" (PendingAccount).
 * Es la fuente única que cualquier pantalla usa para mostrar que un cliente debe.
 *
 * @param totalCents  suma de [amountCents] de todas las cuentas activas del cliente.
 * @param accountCount número de cuentas activas.
 * @param hasOverdue  true si al menos una cuenta está vencida (dueDate < hoy).
 */
data class ClientDebt(
    val clientId: String,
    val totalCents: Long,
    val accountCount: Int,
    val hasOverdue: Boolean,
) {
    val formattedTotal: String get() = formatQuetzales(totalCents)
}

/** Formatea centavos a Quetzales: 45000 → "Q450", 145050 → "Q1,450.50". */
fun formatQuetzales(cents: Long): String {
    val value = cents / 100.0
    val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    val pattern = if (cents % 100L == 0L) "#,##0" else "#,##0.00"
    return "Q" + DecimalFormat(pattern, symbols).format(value)
}
