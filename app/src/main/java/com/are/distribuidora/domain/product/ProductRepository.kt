package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

import androidx.paging.PagingData

interface ProductRepository {
    fun getProductsStream(query: String?): Flow<PagingData<Product>>

    suspend fun getById(id: com.are.distribuidora.domain.valueobject.ProductId): Product?

    fun observeById(id: com.are.distribuidora.domain.valueobject.ProductId): Flow<Product?>

    suspend fun save(product: Product)

    suspend fun delete(id: String)

    suspend fun countAll(): Int

    fun getSyncStatuses(): Flow<Map<String, com.are.distribuidora.data.local.SyncStatus>>
}
