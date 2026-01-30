package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getProducts(): Flow<List<Product>>

    suspend fun getById(id: com.are.distribuidora.domain.valueobject.ProductId): Product?

    suspend fun save(product: Product)
}
