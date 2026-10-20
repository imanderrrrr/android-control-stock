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
import kotlinx.coroutines.sync.withLock

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

        // Esperar y no saltarse: quien pide una bajada con otra en curso la pide porque algo cambió
        // en el servidor después de que esa empezó (venta subida, vale, pantalla abierta). Además
        // scheduleOneTimeNow encola con REPLACE: cancela el worker en curso, que puede tardar en
        // soltar el mutex (p. ej. dentro de una transacción de Room); saltarse ahí dejaba la
        // bajada cancelada y ninguna en su lugar. La espera es cancelable y la bajada incremental.
        if (syncMutex.isLocked) {
            logger.d(TAG, "Sync already in progress - waiting for it to finish")
        }

        return syncMutex.withLock {
            runSync()
        }
    }

    private suspend fun runSync(): Result<Unit> {
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
        }
    }
}
