package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.core.sync.SyncDebug
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.usecase.SyncPedidosUseCase
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
                Log.e(
                    SyncDebug.TAG_SYNC,
                    "${SyncDebug.prefix(runId)} PEDIDO_WORKER_RESULT failure willRetry=${runAttemptCount < 3} ex=${ex?.javaClass?.simpleName} msg=${ex?.message}",
                    ex
                )

                val isTransient = ex is java.io.IOException ||
                    (ex is com.google.firebase.firestore.FirebaseFirestoreException &&
                        (ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ||
                         ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                         ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))

                if (isTransient && runAttemptCount < 3) {
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
}

