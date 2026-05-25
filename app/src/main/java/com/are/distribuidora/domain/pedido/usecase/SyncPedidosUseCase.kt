package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository
import kotlinx.coroutines.CancellationException

private const val TAG = "SYNC_PEDIDO"
private const val BATCH_LIMIT = 20

/**
 * Caso de uso de dominio: sincronización ascendente de pedidos (local → Firestore).
 *
 * Procesa dos colas en el mismo pipeline:
 *  1. PENDING_CREATE / SYNCING  → [repository.uploadAndMarkSynced]    (creación)
 *  2. PENDING_UPDATE            → [repository.updateAndMarkSynced]    (edición)
 *
 * Exclusión mutua: garantizada por enqueueUniqueWork(KEEP) + estado SYNCING en Room.
 */
class SyncPedidosUseCase(
    private val repository: PedidoRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val logger: Logger
) {

    suspend operator fun invoke(): Result<Unit> {
        logger.d(TAG, "SyncPedidosUseCase started")

        val isOnline = connectivityChecker.isOnline()
        logger.d(TAG, "Network available: $isOnline")

        if (!isOnline) {
            logger.d(TAG, "No network - skipping pedido sync")
            return Result.success(Unit)
        }

        return try {
            // ── Cola 1: creaciones nuevas (PENDING_CREATE / SYNCING) ────────
            val pendingCreate = repository.getPendingPedidosForSync(BATCH_LIMIT)
            logger.d(TAG, "Found ${pendingCreate.size} pending-create pedidos to sync")

            var successCount = 0
            var failureCount = 0

            for (pedidoWithItems in pendingCreate) {
                val pedidoId = pedidoWithItems.pedido.id
                try {
                    logger.d(TAG, "Uploading (create) pedido $pedidoId (items=${pedidoWithItems.items.size})")
                    repository.uploadAndMarkSynced(pedidoWithItems)
                    successCount++
                    logger.d(TAG, "Pedido $pedidoId synced OK (create)")
                } catch (e: CancellationException) {
                    logger.d(TAG, "Pedido $pedidoId upload cancelled — repo reverted, re-throwing")
                    throw e
                } catch (e: Exception) {
                    failureCount++
                    logger.e(TAG, "Failed to upload pedido $pedidoId — reverted to PENDING_CREATE", e)
                }
            }

            // ── Cola 2: actualizaciones editadas (PENDING_UPDATE) ───────────
            val pendingUpdate = repository.getPendingUpdatePedidosForSync(BATCH_LIMIT)
            logger.d(TAG, "Found ${pendingUpdate.size} pending-update pedidos to sync")

            for (pedidoWithItems in pendingUpdate) {
                val pedidoId = pedidoWithItems.pedido.id
                try {
                    logger.d(TAG, "Uploading (update) pedido $pedidoId (items=${pedidoWithItems.items.size})")
                    repository.updateAndMarkSynced(pedidoWithItems)
                    successCount++
                    logger.d(TAG, "Pedido $pedidoId synced OK (update)")
                } catch (e: CancellationException) {
                    logger.d(TAG, "Pedido $pedidoId update cancelled — repo reverted, re-throwing")
                    throw e
                } catch (e: Exception) {
                    failureCount++
                    logger.e(TAG, "Failed to update pedido $pedidoId — reverted to PENDING_UPDATE", e)
                }
            }

            logger.d(TAG, "SyncPedidosUseCase finished: success=$successCount failure=$failureCount")

            if (failureCount > 0 && successCount == 0) {
                throw RuntimeException("All $failureCount pedido(s) failed to sync")
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            logger.d(TAG, "SyncPedidosUseCase cancelled")
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "SyncPedidosUseCase failed", e)
            Result.failure(e)
        }
    }
}



