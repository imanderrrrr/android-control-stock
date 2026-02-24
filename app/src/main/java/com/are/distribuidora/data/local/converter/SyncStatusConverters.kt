package com.are.distribuidora.data.local.converter

import androidx.room.TypeConverter
import com.are.distribuidora.data.local.SyncStatus

class SyncStatusConverters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String {
        return value.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        // Strict conversion: throws IllegalArgumentException if value is unknown.
        // No silent fallback to FAILED.
        return SyncStatus.valueOf(value)
    }
}
