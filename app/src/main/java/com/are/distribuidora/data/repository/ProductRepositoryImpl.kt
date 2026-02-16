package com.are.distribuidora.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.are.distribuidora.core.sync.SyncDebug
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.mapper.toDomain
import com.are.distribuidora.data.mapper.toDomainOrNull
import com.are.distribuidora.data.mapper.toEntity
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val TAG = SyncDebug.TAG_SYNC

/**
 * Orquesta la obtención de datos desde fuentes 'puras' (LocalProductRepository)
 * y aplica la transformación a Dominio.
 */
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val coordinator: com.are.distribuidora.workers.ProductSyncCoordinator,
) : ProductRepository {


    override fun getProductsStream(query: String?): Flow<PagingData<Product>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { productDao.productsPagingSource(query) }
        ).flow
            .map { pagingData ->
                pagingData.map { it.toDomain() }
            }
    }

    override suspend fun getById(id: com.are.distribuidora.domain.valueobject.ProductId): Product? {
        Log.d(TAG, "ProductRepository.getById: ${id.value}")
        return productDao.getById(id.value)?.toDomainOrNull()
    }

    override fun observeById(id: com.are.distribuidora.domain.valueobject.ProductId): Flow<Product?> {
        return productDao.observeById(id.value)
            .map { entity ->
                entity?.toDomainOrNull()
            }
    }

    override suspend fun save(product: Product) {
        val ts = System.currentTimeMillis()
        Log.d(TAG, "ProductRepository.save: ${product.id.value}")

        val existing = productDao.getById(product.id.value)

        if (existing == null) {
            Log.d(TAG, "Creating new product ${product.id.value} with updatedAt=0")
            // Create new
            val entity = product.toEntity(com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE)
                .copy(updatedAt = 0L)

            val rowId = productDao.insert(entity)
            Log.d(TAG, "${SyncDebug.prefix(runId = null)} REPO_SAVE_OK op=CREATE productId=${product.id.value} rowId=$rowId")
        } else {
            // Update existing
            Log.d(TAG, "Updating existing product ${product.id.value} preserving updatedAt=${existing.updatedAt}")

            val nextStatus = when (existing.syncStatus) {
                // Si nunca se sincronizó, sigue siendo CREATE aunque el usuario edite varias veces.
                com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE -> com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE

                // Si está pendiente de borrar, una edición normal no debe resucitarlo silenciosamente.
                com.are.distribuidora.data.local.SyncStatus.PENDING_DELETE -> com.are.distribuidora.data.local.SyncStatus.PENDING_DELETE

                // Caso normal: producto ya existe remotamente o estaba sincronizándose ̶ cualquier edición lo vuelve a dejar pending update.
                else -> com.are.distribuidora.data.local.SyncStatus.PENDING_UPDATE
            }

            val entity = product.toEntity(nextStatus).copy(
                createdAt = existing.createdAt,
                updatedAt = existing.updatedAt,
                lastSyncedAt = existing.lastSyncedAt,
                isDeleted = existing.isDeleted // no permitir cambiar isDeleted desde edición común
            )

            productDao.update(entity)
            Log.d(
                TAG,
                "${SyncDebug.prefix(runId = null)} REPO_SAVE_OK op=UPDATE productId=${product.id.value} prevSyncStatus=${existing.syncStatus} newSyncStatus=${entity.syncStatus} preservedUpdatedAt=${existing.updatedAt} lastSyncedAt=${existing.lastSyncedAt}"
            )
        }

        // Trigger Immediate Sync Coordination
        coordinator.notifyLocalChange(source = "REPO_SAVE")
    }

    override suspend fun delete(id: String) {
        productDao.markAsPendingDelete(id)

        Log.d(TAG, "${SyncDebug.prefix(runId = null)} REPO_DELETE_END productId=$id newSyncStatus=PENDING_DELETE")

        // Trigger Immediate Sync Coordination
        coordinator.notifyLocalChange(source = "REPO_DELETE")
    }

    override fun getSyncStatuses(): Flow<Map<String, com.are.distribuidora.data.local.SyncStatus>> {
        return productDao.getSyncStatuses()
            .map { list ->
                list.associate { it.id to it.syncStatus }
            }
    }
}
