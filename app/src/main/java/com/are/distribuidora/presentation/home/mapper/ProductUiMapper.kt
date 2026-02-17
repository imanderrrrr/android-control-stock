package com.are.distribuidora.presentation.home.mapper

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.presentation.home.model.ProductUiModel

fun Product.toUiModel(syncStatus: SyncStatus?): ProductUiModel {
    val status = syncStatus ?: SyncStatus.SYNCED

    val syncIndicatorText = when (status) {
        SyncStatus.SYNCED -> "🟢 Sincronizado"
        SyncStatus.SYNCING -> "🟡 Sincronizando"
        SyncStatus.PENDING,
        SyncStatus.PENDING_UPDATE,
        SyncStatus.PENDING_CREATE,
        SyncStatus.PENDING_DELETE,
        SyncStatus.FAILED,
        SyncStatus.ERROR
        -> "🔴 No sincronizado"
        SyncStatus.CONFLICT -> "🔴 Conflicto"
    }

    val activeIndicatorText = if (isActive) {
        "🟢 Activo"
    } else {
        "🔴 Inactivo"
    }

    return ProductUiModel(
        product = this,
        isActive = isActive,
        syncStatus = status,
        rawSyncStatus = syncStatus,
        syncIndicatorText = syncIndicatorText,
        activeIndicatorText = activeIndicatorText
    )
}
