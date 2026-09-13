package com.are.distribuidora.stockmovement.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.stockmovement.data.local.dao.StockMovementDao
import com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity
import com.are.distribuidora.stockmovement.data.remote.StockMovementRemoteDataSource
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.repository.StockMovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "SYNC_MOVEMENTS"

class StockMovementRepositoryImpl(
    private val database: DistribuidoraDatabase,
    private val movementDao: StockMovementDao,
    private val productDao: ProductDao,
    private val remote: StockMovementRemoteDataSource,
    /** Dispara el worker de productos (que sube movimientos y luego baja el catálogo). */
    private val onLocalChange: (source: String) -> Unit,
) : StockMovementRepository {

    override suspend fun recordVoucher(movement: StockMovement) {
        database.withTransaction {
            val inserted = movementDao.insert(StockMovementEntity.fromDomain(movement, SyncStatus.PENDING_CREATE))
            if (inserted != -1L) {
                productDao.applyMovement(movement.productId, movement.signedQuantity)
            }
        }
        Log.i(TAG, "recordVoucher: id=${movement.id} product=${movement.productId} ${movement.type} ${movement.quantity} ${movement.reason}")
        onLocalChange("VOUCHER")
    }

    override fun observeByProduct(productId: String, limit: Int): Flow<List<StockMovement>> =
        movementDao.observeByProduct(productId, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun uploadPending() {
        movementDao.resetStaleSyncing()
        val pending = movementDao.getPendingStandalone(BATCH)
        if (pending.isEmpty()) {
            Log.d(TAG, "uploadPending: nada pendiente")
            return
        }
        val ids = pending.map { it.id }
        movementDao.markSyncing(ids)
        try {
            val persisted = remote.uploadMovements(pending.map { it.toDomain() })
            movementDao.markSynced(persisted, System.currentTimeMillis())
            Log.i(TAG, "uploadPending: synced=${persisted.size}")
        } catch (e: Exception) {
            movementDao.revertSyncingToPending(ids)
            throw e
        }
    }

    private companion object {
        const val BATCH = 200
    }
}
