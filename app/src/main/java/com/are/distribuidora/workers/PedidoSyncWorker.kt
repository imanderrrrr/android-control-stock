package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.core.sync.SyncDebug
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.usecase.PedidoSyncIncompleteException
import com.are.distribuidora.domain.pedido.usecase.SyncPedidosUseCase
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.UUID

@HiltWorker
class PedidoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncPedidosUseCase: SyncPedidosUseCase,
    private val logger: Logger
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val inputRunId = inputData.getString(SyncDebug.KEY_RUN_ID)
        val runId = if (!inputRunId.isNullOrEmpty()) inputRunId
                    else "PEDIDO-${UUID.randomUUID().toString().take(8)}"

        Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} PEDIDO_WORKER_START attempt=$runAttemptCount")

        return try {
            val result = syncPedidosUseCase()

            if (result.isSuccess) {
                Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} PEDIDO_WORKER_RESULT success workerId=$id")
                Result.success()
            } else {
                val ex = result.exceptionOrNull()
                val willRetry = ex?.isTransient() == true && runAttemptCount < MAX_RETRIES
                Log.e(
                    SyncDebug.TAG_SYNC,
                    "${SyncDebug.prefix(runId)} PEDIDO_WORKER_RESULT failure willRetry=$willRetry ex=${ex?.javaClass?.simpleName} msg=${ex?.message}",
                    ex
                )

                if (willRetry) {
                    Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} PEDIDO_WORKER_RETRY scheduling retry")
                    Result.retry()
                } else {
                    Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} PEDIDO_WORKER_FAILURE final failure")
                    Result.failure()
                }
            }
        } catch (e: CancellationException) {
            // Re-lanzar siempre: CoroutineWorker depende de CancellationException para
            // detener la coroutine limpiamente. Atraparla y devolver Result.failure()
            // enmascararía la cancelación y podría confundir el estado del trabajo en
            // WorkManager. El repositorio ya revirtió los SYNCING a PENDING_CREATE.
            Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} PEDIDO_WORKER_CANCELLED — rethrowing for WorkManager")
            throw e
        } catch (e: Exception) {
            Log.e(
                SyncDebug.TAG_SYNC,
                "${SyncDebug.prefix(runId)} PEDIDO_WORKER_CRASH attempt=$runAttemptCount ex=${e::class.java.simpleName} msg=${e.message}",
                e
            )
            Result.failure()
        }
    }

    private companion object {
        /**
         * Reintentos con el backoff por defecto (30 s, 1 min, 2 min). Tras el tope, el periódico de
         * [PedidoSyncScheduler] recoge lo que quede; sin tope el backoff crecería hasta 5 h y, con
         * KEEP, ese trabajo en espera absorbería los disparos de las confirmaciones siguientes.
         */
        const val MAX_RETRIES = 3

        val TRANSIENT_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
        )

        /** Mira el error original aunque venga envuelto: el caso de uso agrupa los de cada pedido. */
        fun Throwable.isTransient(): Boolean = when (this) {
            is PedidoSyncIncompleteException -> causes.any { it.isTransient() }
            is java.io.IOException -> true
            is FirebaseFirestoreException -> code in TRANSIENT_CODES
            else -> cause?.isTransient() == true
        }
    }
}

