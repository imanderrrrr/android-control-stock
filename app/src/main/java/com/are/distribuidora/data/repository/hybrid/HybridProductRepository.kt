package com.are.distribuidora.data.repository.hybrid

import com.are.distribuidora.domain.product.ProductSyncRepository
import javax.inject.Inject

/**
 * LEGACY.
 *
 * Este repositorio existía como híbrido (remoto->local + mapping + manejo de errores + logging).
 *
 * Tras el refactor Clean:
 * - El flujo vive en el caso de uso `SyncProductsUseCase` (dominio).
 * - El I/O y el mapping viven en `ProductSyncRepositoryImpl` (data).
 *
 * Se mantiene como wrapper para no romper tests/consumidores legacy;
 * no debe contener lógica de flujo.
 */
@Deprecated(
    message = "Usar SyncProductsUseCase (dominio) + ProductSyncRepository (data).",
    level = DeprecationLevel.WARNING,
)
class HybridProductRepository @Inject constructor(
    private val productSyncRepository: ProductSyncRepository,
) {
    suspend fun syncProducts() {
        productSyncRepository.syncProductsRemoteToLocal()
    }
}
