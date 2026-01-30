package com.are.distribuidora.data.repository.local

import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalProductRepository @Inject constructor(
    private val productDao: ProductDao,
) {

    fun observeProductEntities(): Flow<List<ProductEntity>> =
        productDao.observeProducts()

    suspend fun getById(id: String): ProductEntity? =
        productDao.getById(id)

    suspend fun upsert(entity: ProductEntity) =
        productDao.upsert(entity)
}
