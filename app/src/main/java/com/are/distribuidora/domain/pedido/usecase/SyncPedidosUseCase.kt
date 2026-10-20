package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SYNC_PEDIDO"
private const val BATCH_LIMIT = 20

/** No hay red validada: no se intentó subir nada y la ejecución debe reintentarse. */
class PedidoSyncOfflineException : java.io.IOException("Sin red: no se subió ningún pedido")

/**
 * La ejecución terminó con pedidos sin subir. [causes] trae el error original de cada uno, sin
 * envolver, para que el worker decida si un reintento puede arreglarlo.
 */
class PedidoSyncIncompleteException(
    val causes: List<Throwable>,
    syncedCount: Int,
) : RuntimeException("${causes.size} pedido(s) sin subir ($syncedCount subidos)", causes.firstOrNull())

/**
 * Caso de uso de dominio: sincronización ascendente de pedidos (local → Firestore).
 *
 * Procesa dos colas en el mismo pipeline, en lotes de [BATCH_LIMIT] hasta vaciarlas:
 *  1. PENDING_CREATE / SYNCING  → [repository.uploadAndMarkSynced]    (creación)
 *  2. PENDING_UPDATE            → [repository.updateAndMarkSynced]    (edición)
 *
 * Solo devuelve éxito si no quedó nada sin subir: sin red o con algún pedido fallido devuelve
 * fallo para que el worker reintente. Reintentar es seguro porque la subida es idempotente.
 *
 * Exclusión mutua: el worker inmediato y el periódico usan uniqueNames distintos, así que pueden
 * coincidir; [mutex] serializa sus ejecuciones (la cola de creación incluye SYNCING, y sin él ambas
 * subirían el mismo pedido a la vez).
 */
class SyncPedidosUseCase(
    private val repository: PedidoRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val logger: Logger
) {

    private val mutex = Mutex()

    suspend operator fun invoke(): Result<Unit> = mutex.withLock { sync() }

    private suspend fun sync(): Result<Unit> {
        logger.d(TAG, "SyncPedidosUseCase started")

        val isOnline = connectivityChecker.isOnline()
        logger.d(TAG, "Network available: $isOnline")

        if (!isOnline) {
            logger.d(TAG, "No network - pedido sync must be retried")
            return Result.failure(PedidoSyncOfflineException())
        }

        return try {
            val run = UploadRun()

            // Se repite hasta una vuelta sin nada que subir: así entran en esta misma ejecución los
            // pedidos confirmados o editados mientras se subían otros, cuyo disparo KEEP se descartó.
            do {
                val attemptedThisRound =
                    drain("create", run, repository::getPendingPedidosForSync, repository::uploadAndMarkSynced) +
                    drain("update", run, repository::getPendingUpdatePedidosForSync, repository::updateAndMarkSynced)
            } while (attemptedThisRound > 0)

            logger.d(TAG, "SyncPedidosUseCase finished: success=${run.syncedCount} failure=${run.failures.size}")

            if (run.failures.isNotEmpty()) {
                Result.failure(PedidoSyncIncompleteException(run.failures.values.toList(), run.syncedCount))
            } else {
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            logger.d(TAG, "SyncPedidosUseCase cancelled")
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "SyncPedidosUseCase failed", e)
            Result.failure(e)
        }
    }

    /** Estado de una ejecución: qué se intentó, qué falló y cuántos subieron. */
    private class UploadRun {
        /** "cola:id@versión" ya intentados: una edición nueva (otra versión) sí se vuelve a subir. */
        val attempted = mutableSetOf<String>()
        val failures = linkedMapOf<String, Throwable>()
        var syncedCount = 0
    }

    /**
     * Sube una cola en lotes hasta vaciarla y devuelve cuántos pedidos intentó. Un mismo pedido con
     * la misma versión se intenta una sola vez por ejecución: si falló, sigue pendiente en el
     * repositorio y lo reintenta el worker; si sigue en la cola tras subir, lo recoge la siguiente
     * ejecución. Así el bucle siempre termina.
     */
    private suspend fun drain(
        queue: String,
        run: UploadRun,
        fetch: suspend (limit: Int) -> List<PedidoWithItems>,
        upload: suspend (PedidoWithItems) -> Unit,
    ): Int {
        var attempted = 0
        while (true) {
            // Se piden además tantos como ya intentados: los que sigan en la cola no deben tapar al resto.
            val batch = fetch(BATCH_LIMIT + run.attempted.size)
                .filter { attemptKey(queue, it) !in run.attempted }
                .take(BATCH_LIMIT)
            if (batch.isEmpty()) return attempted
            logger.d(TAG, "Found ${batch.size} pending-$queue pedidos to sync")

            for (pedidoWithItems in batch) {
                val pedidoId = pedidoWithItems.pedido.id
                run.attempted += attemptKey(queue, pedidoWithItems)
                attempted++
                try {
                    logger.d(TAG, "Uploading ($queue) pedido $pedidoId (items=${pedidoWithItems.items.size})")
                    upload(pedidoWithItems)
                    run.failures.remove(pedidoId)
                    run.syncedCount++
                    logger.d(TAG, "Pedido $pedidoId synced OK ($queue)")
                } catch (e: CancellationException) {
                    logger.d(TAG, "Pedido $pedidoId $queue cancelled — repo reverted, re-throwing")
                    throw e
                } catch (e: Exception) {
                    run.failures[pedidoId] = e
                    logger.e(TAG, "Failed to $queue pedido $pedidoId — reverted to pending", e)
                }
            }
        }
    }

    private fun attemptKey(queue: String, pedidoWithItems: PedidoWithItems): String =
        "$queue:${pedidoWithItems.pedido.id}@${pedidoWithItems.pedido.version}"
}
