package com.are.distribuidora.data.local.model

import androidx.room.ColumnInfo
import com.are.distribuidora.data.local.SyncStatus

data class ProductSyncStatusTuple(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "syncStatus") val syncStatus: SyncStatus,
    @ColumnInfo(name = "isActive") val isActive: Boolean
)
