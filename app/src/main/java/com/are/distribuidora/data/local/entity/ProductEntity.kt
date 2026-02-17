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
    val imageUrl: String?,
    val barcode: String?,
    val stock: Int,
    
    val isActive: Boolean = true, // Default true to match Client structure suggestion/requirement
    val isDeleted: Boolean = false,
    
    val syncStatus: com.are.distribuidora.data.local.SyncStatus,
    
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null,
)
