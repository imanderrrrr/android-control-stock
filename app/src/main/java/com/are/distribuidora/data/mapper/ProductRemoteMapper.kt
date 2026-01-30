package com.are.distribuidora.data.mapper

import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource

/** Mapper puramente técnico (data): RemoteProduct -> ProductEntity. */
internal fun ProductRemoteDataSource.RemoteProduct.toEntityOrNull(): ProductEntity? {
    if ((stock ?: 0) < 0) return null
    return ProductEntity(
        id = id,
        name = name,
        price = price ?: 0.0,
        stock = stock ?: 0,
    )
}


