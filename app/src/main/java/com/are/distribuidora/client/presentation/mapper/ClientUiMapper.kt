package com.are.distribuidora.client.presentation.mapper

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.are.distribuidora.data.local.SyncStatus

fun Client.toUiModel(): ClientUiModel {
    val syncIndicatorText = when (syncStatus) {
        SyncStatus.SYNCED -> "🟢 Sincronizado"
        SyncStatus.SYNCING -> "🟡 Sincronizando"
        SyncStatus.PENDING,
        SyncStatus.PENDING_UPDATE,
        SyncStatus.PENDING_CREATE,
        SyncStatus.PENDING_DELETE,
        SyncStatus.FAILED,
        SyncStatus.ERROR,
        -> "🔴 No sincronizado"
    }

    val activeIndicatorText = if (isActive) {
        "🟢 Activo"
    } else {
        "🔴 Inactivo"
    }

    return ClientUiModel(
        id = id,
        name = name,
        phone = phone.orEmpty(),
        address = address,
        isActive = isActive,
        syncStatus = syncStatus,
        syncIndicatorText = syncIndicatorText,
        activeIndicatorText = activeIndicatorText,
    )
}
