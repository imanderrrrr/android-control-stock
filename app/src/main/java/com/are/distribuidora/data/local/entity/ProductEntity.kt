package com.are.distribuidora.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = false)])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val category: String?,
    val price: Double,
    /** URL remota (https://...) de Firebase Storage. Campo autoritativo — coincide con Firestore "imageUrl". */
    val imageUrl: String?,
    /** URI local de la imagen (solo para renderizar en este dispositivo). NUNCA se sube a Firestore. */
    val imageLocalUri: String? = null,
    val barcode: String?,
    /** Existencias (puede ser negativo desde 4.1). Ver [com.are.distribuidora.stockmovement]. */
    val stock: Int,

    val isActive: Boolean = true, // Default true to match Client structure suggestion/requirement
    val isDeleted: Boolean = false,
    
    val syncStatus: com.are.distribuidora.data.local.SyncStatus,
    
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null,
)
