package com.are.distribuidora.data.repository

import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.data.mapper.toDomainOrNull
import com.are.distribuidora.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementación REAL del repositorio de dominio.
 * Orquesta la obtención de datos desde fuentes 'puras' (LocalProductRepository)
 * y aplica la transformación a Dominio.
 */
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
) : ProductRepository {

    override fun getProducts(): Flow<List<Product>> {
        return productDao.observeProducts()
            .map { entities ->
                entities.mapNotNull { it.toDomainOrNull() }
            }
    }

    override suspend fun getById(id: com.are.distribuidora.domain.valueobject.ProductId): Product? {
        return productDao.getById(id.value)?.toDomainOrNull()
    }

    override suspend fun save(product: Product) {
        productDao.upsert(product.toEntity())
    }
}
