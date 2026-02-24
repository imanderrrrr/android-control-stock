package com.are.distribuidora.presentation.home.model

import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product

data class ProductUiModel(
    val product: Product,
    val isActive: Boolean,
    val syncState: SyncState,
    val rawSyncState: SyncState?,
    val syncIndicatorText: String,
    val activeIndicatorText: String
)
