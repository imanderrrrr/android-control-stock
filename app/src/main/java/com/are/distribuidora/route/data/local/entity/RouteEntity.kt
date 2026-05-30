package com.are.distribuidora.route.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.are.distribuidora.data.local.SyncStatus

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 1..7 (Lunes..Domingo) */
    val deliveryDay: Int,
    /** Estado de sincronización usando SyncStatus. */
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft-delete: true = marcada para eliminación. Se purga tras 2 días. */
    val isDeleted: Boolean = false,
)
