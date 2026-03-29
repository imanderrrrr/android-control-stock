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
        // FIX: fallback seguro en lugar de crash con datos legacy o corruptos.
        // La BD ha pasado por 33 migraciones; un valor desconocido devuelve FAILED
        // (estado reintentable) en lugar de lanzar IllegalArgumentException.
        return try {
            SyncStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            SyncStatus.FAILED
        }
    }
}
