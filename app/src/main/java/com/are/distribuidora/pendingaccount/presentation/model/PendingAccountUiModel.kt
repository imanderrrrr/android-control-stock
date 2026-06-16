package com.are.distribuidora.pendingaccount.presentation.model

/** Estado de la fecha límite, para colorear la tarjeta. */
enum class DueState { OVERDUE, SOON, NORMAL }

/**
 * UI model de una cuenta por cobrar activa (rediseño Bosque Pro).
 * Los textos derivados (monto, fecha, banner) se calculan en el ViewModel.
 */
data class PendingAccountUiModel(
    val id: String,
    val routeId: String,
    val clientId: String,
    val clientName: String,
    val routeName: String,
    val amountCents: Long,
    /** Monto formateado, p. ej. "Q820" / "Q1,450.50". */
    val amountFormatted: String,
    val invoicePhotoUri: String?,
    val invoiceRemoteUrl: String?,
    val dueDateMillis: Long,
    /** Etiqueta de la fecha, p. ej. "Venció el 11 jun" / "Vence hoy" / "Vence el 28 jun". */
    val dueLabel: String,
    val dueState: DueState,
    /** Texto del banner rojo si está vencida ("VENCIDA · HACE 4 DÍAS"), null si no. */
    val overdueBannerText: String?,
    val notes: String?,
    /** Fecha de registro formateada ("1 jun 2026") para el detalle. */
    val createdAtFormatted: String,
) {
    val isOverdue: Boolean get() = dueState == DueState.OVERDUE
    val hasInvoice: Boolean
        get() = !invoicePhotoUri.isNullOrBlank() || !invoiceRemoteUrl.isNullOrBlank()
}
