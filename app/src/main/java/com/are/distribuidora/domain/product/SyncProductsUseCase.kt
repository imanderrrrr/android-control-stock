package com.are.distribuidora.domain.product

/**
 * Caso de uso de dominio: sincronización descendente de productos (remoto -> local).
 *
 * Regla Clean:
 * - Toda decisión de flujo vive aquí.
 * - El repositorio solo hace I/O técnico.
 */
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import kotlinx.coroutines.sync.Mutex

private const val TAG = "SYNC_PRODUCT"

/**
 * Caso de uso de dominio: sincronización descendente de productos (remoto -> local).
 *
 * Regla Clean:
 * - Toda decisión de flujo vive aquí.
 * - El repositorio solo hace I/O técnico.
 */
class SyncProductsUseCase(
    private val repository: ProductSyncRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val logger: Logger
) {
    private val syncMutex = Mutex()

    suspend operator fun invoke(): Result<Unit> {
        logger.d(TAG, "SyncProductsUseCase started")

        val isOnline = connectivityChecker.isOnline()
        logger.d(TAG, "Network available: $isOnline")

        if (!isOnline) {
            logger.d(TAG, "No network - skipping sync")
            return Result.success(Unit)
        }

        if (!syncMutex.tryLock()) {
            logger.d(TAG, "Sync already in progress - skipping")
            return Result.success(Unit)
        }

        return try {
            logger.d(TAG, "Starting upload pending products")
            repository.uploadPendingProducts()
            logger.d(TAG, "Upload pending completed")

            logger.d(TAG, "Starting downstream sync (flow + batch)")
            repository.syncDownstream()
            logger.d(TAG, "Downstream sync completed")

            logger.d(TAG, "SyncProductsUseCase finished successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "SyncProductsUseCase failed with exception", e)
            Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }
}
