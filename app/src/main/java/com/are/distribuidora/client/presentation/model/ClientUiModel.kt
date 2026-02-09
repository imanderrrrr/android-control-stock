package com.are.distribuidora.client.presentation.model

import com.are.distribuidora.data.local.SyncStatus

data class ClientUiModel(
    val id: String,
    val name: String,
    val phone: String,
    val address: String?,
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val syncIndicatorText: String,
    val activeIndicatorText: String,
)
