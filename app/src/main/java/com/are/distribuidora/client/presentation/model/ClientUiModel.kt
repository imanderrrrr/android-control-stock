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
)
