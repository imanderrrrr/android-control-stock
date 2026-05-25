package com.are.distribuidora.presentation.home.model

/**
 * UI model para una cuenta pendiente de cobro.
 */
data class PendingAccountUiModel(
    val id: String,
    val routeId: String,
    val clientId: String,
    val clientName: String,
    val routeName: String,
    /** Monto formateado, e.g. "Q 1,250.00" */
    val amountFormatted: String,
    val amountCents: Long,
    /** Foto de la factura (URI local). */
    val invoicePhotoUri: String?,
    /** URL remota de la foto de la factura (Firebase Storage). */
    val invoiceRemoteUrl: String?,
    /** Fecha de pago formateada, e.g. "15 Mar 2026". */
    val dueDateFormatted: String,
    val dueDateMillis: Long,
    /** true si la fecha de pago ya pasó. */
    val isOverdue: Boolean,
    val isPaid: Boolean,
    val notes: String?,
)

