package com.are.distribuidora.client.presentation.model

import com.are.distribuidora.domain.core.SyncState

data class ClientUiModel(
    val id: String,
    val name: String,
    val phone: String,
    val address: String?,
    val isActive: Boolean,
    val syncState: SyncState,
    val syncIndicatorText: String,
    val activeIndicatorText: String,
    // Deuda (cuentas por cobrar). null = no debe.
    val debtCents: Long? = null,
    val debtText: String? = null,        // "DEBE Q820"
    val debtOverdue: Boolean = false,    // vencida → rojo; si no, ámbar
    val attended: Boolean = false,       // ya tiene un pedido hoy
)
