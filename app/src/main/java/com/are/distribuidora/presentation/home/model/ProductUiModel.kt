package com.are.distribuidora.presentation.home.model

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.data.local.SyncStatus

data class ProductUiModel(
    val product: Product, // Keep original product for existing bind logic
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val rawSyncStatus: SyncStatus?, // For debugging: exact value from map
    val syncIndicatorText: String,
    val activeIndicatorText: String
)
