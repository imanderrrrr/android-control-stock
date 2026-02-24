package com.are.distribuidora.presentation.home.mapper

import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.presentation.home.model.ProductUiModel

fun Product.toUiModel(syncState: SyncState?): ProductUiModel {
    val state = syncState ?: SyncState.SYNCED

    val syncIndicatorText = when (state) {
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

    return ProductUiModel(
        product = this,
        isActive = isActive,
        syncState = state,
        rawSyncState = syncState,
        syncIndicatorText = syncIndicatorText,
        activeIndicatorText = activeIndicatorText
    )
}
