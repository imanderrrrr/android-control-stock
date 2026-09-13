package com.are.distribuidora.pendingaccount.presentation.model

/**
 * UI model de una entrada de actividad (cuenta pagada o eliminada).
 */
data class AccountActivityUiModel(
    val id: String,
    /** true = pagada, false = eliminada. */
    val isPaid: Boolean,
    /** Mensaje completo, p. ej. "anderson@… pagó la cuenta de Tienda Angy". */
    val message: String,
    val amountFormatted: String,
    /** Epoch millis de la resolución (para el tiempo relativo, calculado al pintar). */
    val resolvedAtMillis: Long,
)
