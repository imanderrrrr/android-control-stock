package com.are.distribuidora.data.mapper

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.remote.model.RemoteProduct

/** Mapper puramente técnico (data): RemoteProduct -> ProductEntity. */
internal fun RemoteProduct.toEntityOrNull(): ProductEntity? {
    if ((stock ?: 0) < 0) return null
    return ProductEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        price = price ?: 0.0,
        imageUrl = imageUrl,
        barcode = barcode,
        stock = stock ?: 0,
        isActive = isActive ?: true,
        isDeleted = isDeleted ?: false,
        syncStatus = SyncStatus.SYNCED,
        createdAt = createdRemoteAt ?: 0L,
        updatedAt = updatedRemoteAt ?: 0L,
        lastSyncedAt = null
    )
}


