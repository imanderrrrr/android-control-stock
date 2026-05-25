package com.are.distribuidora.data.mapper

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.model.RemoteProduct

/** Mapper puramente técnico (data): RemoteProduct -> ProductEntity. */
internal fun RemoteProduct.toEntityOrNull(): ProductEntity? {
    if ((stock ?: 0) < 0) return null
    // Remote imageUrl is always the authoritative remote URL (https://...)
    val remoteImageUrl = imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    return ProductEntity(
        id = id,
        name = name,
        description = description,
        category = category,
        price = price ?: 0.0,
        imageUrl = remoteImageUrl,
        imageLocalUri = null, // Remote doesn't know about local URIs
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


