package com.are.distribuidora.data.repository

import android.util.Log
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.mapper.toEntityOrNull

import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.domain.product.ProductSyncRepository
import javax.inject.Inject

/**
 * Implementación DATA del contrato de dominio [ProductSyncRepository].
 *
 * Regla: solo I/O técnico (descargar + mapear + persistir). Cero decisiones de flujo.
 */
class ProductSyncRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val remote: ProductRemoteDataSource,
) : ProductSyncRepository {

    override suspend fun syncProductsRemoteToLocal() {
        val remoteProducts = remote.fetchAllProducts()
        
        // Flujo: Remote -> Entity -> DB Local.
        // La validación de dominio ocurre al LEER desde la BD (en LocalProductRepository).
        remoteProducts
            .mapNotNull { it.toEntityOrNull() }
            .forEach { productDao.upsert(it) }
    }
}
