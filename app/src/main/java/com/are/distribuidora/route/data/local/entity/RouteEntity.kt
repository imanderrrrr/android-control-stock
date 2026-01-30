package com.are.distribuidora.route.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** 1..7 (Lunes..Domingo) */
    val deliveryDay: Int,
    /** Indica si esta ruta ya fue sincronizada a Firestore. */
    val synced: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
