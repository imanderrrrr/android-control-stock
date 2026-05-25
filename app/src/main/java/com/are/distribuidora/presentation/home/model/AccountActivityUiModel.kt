package com.are.distribuidora.presentation.home.model

/**
 * UI model para una entrada de actividad (cuenta pagada o eliminada).
 */
data class AccountActivityUiModel(
    val id: String,
    /** Mensaje completo: "user@email.com marcó como pagada la cuenta de Cliente de la ruta X" */
    val message: String,
    /** "PAID" o "DELETED" */
    val actionType: String,
    /** Monto formateado, e.g. "Q 1,250.00" */
    val amountFormatted: String,
    /** Timestamp de la acción (epoch millis). */
    val resolvedAt: Long,
)
