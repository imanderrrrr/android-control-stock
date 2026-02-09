package com.are.distribuidora.client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.are.distribuidora.route.data.local.entity.RouteEntity

@Entity(
    tableName = "clients",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["routeId"], name = "index_clients_routeId")]
)
data class ClientEntity(
    @PrimaryKey val id: String,

    val name: String,
    val phone: String?,
    val address: String?,

    val latitude: Double?,
    val longitude: Double?,

    val maxOrderAmountInCents: Long?,

    val isActive: Boolean,
    val isDeleted: Boolean = false,

    val routeId: String,

    val syncStatus: com.are.distribuidora.data.local.SyncStatus,

    val createdAt: Long,
    val updatedAt: Long,

    val createdBy: String,
    val lastModifiedBy: String,

    val lastSyncedAt: Long? = null
)
