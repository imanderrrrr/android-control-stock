package com.are.distribuidora.data.mapper

import android.util.Log
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity

private const val TAG = "ProductMapper"

fun ProductEntity.toDomainOrNull(): Product? {
    val normalizedId = id.trim()
    val normalizedName = name.trim()

    if (normalizedId.isBlank()) {
        Log.e(TAG, "Producto rechazado: ID vacío. entity=$this")
        return null
    }

    if (normalizedName.isBlank()) {
        Log.e(TAG, "Producto rechazado: NAME vacío. id=$normalizedId entity=$this")
        return null
    }

    if (price < 0) {
        Log.e(TAG, "Producto rechazado: PRICE negativo ($price). id=$normalizedId entity=$this")
        return null
    }

    val safeStock = if (stock < 0) {
        Log.w(TAG, "Stock negativo normalizado a 0. id=$normalizedId stock=$stock")
        0
    } else {
        stock
    }

    return try {
        Product(
            id = ProductId.of(normalizedId),
            name = normalizedName,
            price = Money.of(price.toBigDecimal()),
            stock = Quantity.of(safeStock),
            description = this.description,
            category = this.category,
            imageUrl = this.imageUrl,
            barcode = this.barcode,
            isActive = this.isActive,
            isDeleted = this.isDeleted,
            createdAt = this.createdAt,

            updatedAt = this.updatedAt,
        )
    } catch (e: Exception) {
        Log.e(
            TAG,
            "Producto rechazado por excepción de dominio. id=$normalizedId entity=$this",
            e
        )
        null
    }
}

fun Product.toEntity(syncStatus: com.are.distribuidora.data.local.SyncStatus): ProductEntity {
    return ProductEntity(
        id = this.id.value,
        name = this.name,
        description = this.description,
        category = this.category,
        price = this.price.amount.toDouble(),
        imageUrl = this.imageUrl,
        barcode = this.barcode,
        stock = this.stock.value,
        isActive = this.isActive,
        isDeleted = this.isDeleted,
        syncStatus = syncStatus,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        lastSyncedAt = null
    )
}
