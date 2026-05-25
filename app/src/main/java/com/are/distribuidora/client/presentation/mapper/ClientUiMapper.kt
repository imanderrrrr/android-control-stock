package com.are.distribuidora.client.presentation.mapper

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.are.distribuidora.domain.core.SyncState

fun Client.toUiModel(): ClientUiModel {
    val syncIndicatorText = when (syncState) {
        SyncState.SYNCED   -> "🟢 Sincronizado"
        SyncState.SYNCING  -> "🟡 Sincronizando"
        SyncState.PENDING  -> "🔴 No sincronizado"
        SyncState.FAILED   -> "🔴 No sincronizado"
        SyncState.CONFLICT -> "🔴 Conflicto"
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
        syncState = syncState,
        syncIndicatorText = syncIndicatorText,
        activeIndicatorText = activeIndicatorText,
    )
}
