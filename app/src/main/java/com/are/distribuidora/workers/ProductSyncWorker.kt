package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.core.sync.SyncDebug
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.product.SyncProductsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID

@HiltWorker
class ProductSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncProductsUseCase: SyncProductsUseCase,
    private val logger: Logger
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Support both periodic (paramless) and one-time (with runId)
        val inputRunId = inputData.getString(SyncDebug.KEY_RUN_ID)
        val runId = if (!inputRunId.isNullOrEmpty()) inputRunId else "PERIODIC-${UUID.randomUUID().toString().take(8)}"
                
        val tag = "ProductSyncWorker"
        Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} WORKER_START attempt=$runAttemptCount")
        
        return try {
             // Basic metrics logging
             // We can check pending count before starting if desired, but UseCase handles logic.
             
             // UseCase handles all logic (upload -> download)
             // Using 'runId' for logging consistency inside UseCase if supported, or just let UseCase generate its own logs.
             // Here we just wrap the call.
             
             val result = syncProductsUseCase()

            val tsEnd = System.currentTimeMillis()
            Log.d(
                SyncDebug.TAG_SYNC,
                "${SyncDebug.prefix(runId)} WORKER_END workerId=${id} attempt=$runAttemptCount durationMs=${tsEnd - System.currentTimeMillis()} resultIsSuccess=${result.isSuccess}"
            )

            if (result.isSuccess) {
                // Ideally we would log how many items were synced here, but Result might just be Result<Unit>.
                // Assuming success means we are good.
                Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} WORKER_RESULT success")
                Result.success()
            } else {
                val ex = result.exceptionOrNull()
                Log.e(
                    SyncDebug.TAG_SYNC,
                    "${SyncDebug.prefix(runId)} WORKER_RESULT failure willRetry=${runAttemptCount < 3} ex=${ex?.javaClass?.simpleName} msg=${ex?.message}",
                    ex
                )

                // Retry Policy:
                // Retry on Network/Transient errors (IOException, Firestore UNAVAILABLE/DEADLINE_EXCEEDED)
                // Fail on logical errors (JsonParsing, Security, etc)
                val isTransient = ex is java.io.IOException || 
                    (ex is com.google.firebase.firestore.FirebaseFirestoreException && 
                        (ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE || 
                         ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
                         ex.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))

                if (isTransient && runAttemptCount < 3) {
                     Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} WORKER_RETRY scheduling retry")
                     Result.retry()
                } else {
                     Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} WORKER_FAILURE final failure")
                     Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(
                SyncDebug.TAG_SYNC,
                "${SyncDebug.prefix(runId)} WORKER_CRASH attempt=$runAttemptCount ex=${e::class.java.simpleName} msg=${e.message}",
                e
            )
            Result.failure()
        }
    }
}
