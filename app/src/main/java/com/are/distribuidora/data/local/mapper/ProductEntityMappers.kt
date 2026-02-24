package com.are.distribuidora.data.local.mapper

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity

fun ProductEntity.toDomain(): Product = Product(
    id = ProductId.of(id),
    name = name,
    description = description,
    category = category,
    price = Money.of(price.toBigDecimal()),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = Quantity.of(stock),
    comprometido = comprometido,
    isActive = isActive,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Product.toEntity(syncStatus: SyncStatus): ProductEntity = ProductEntity(
    id = id.value,
    name = name,
    description = description,
    category = category,
    price = price.amount.toDouble(),
    imageUrl = imageUrl,
    barcode = barcode,
    stock = stock.value,
    comprometido = comprometido,
    isActive = isActive,
    isDeleted = isDeleted,
    syncStatus = syncStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = null // Managed by Repository/Sync logic
)
