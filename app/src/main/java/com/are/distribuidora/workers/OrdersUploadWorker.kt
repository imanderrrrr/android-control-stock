package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.core.result.Result as DomainResult
import com.are.distribuidora.core.sync.SyncDebug
import com.are.distribuidora.orders.domain.usecase.UploadPendingOrdersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * Sube a Firestore las ediciones locales pendientes de pedidos ajenos (pendingUpload=1).
 *
 * Preserva el dueño original (vendedorId/sellerName) — ver [UploadPendingOrdersUseCase] y
 * FirestoreOrderRemoteDataSource.uploadOrderEdit. Es idempotente: relee la cola de
 * pendientes en cada ejecución y limpia el flag al confirmar cada subida.
 */
@HiltWorker
class OrdersUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadPendingOrdersUseCase: UploadPendingOrdersUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val runId = inputData.getString(SyncDebug.KEY_RUN_ID)
            ?: "ORDERS-${UUID.randomUUID().toString().take(8)}"

        Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_START attempt=$runAttemptCount")

        return try {
            when (val result = uploadPendingOrdersUseCase()) {
                is DomainResult.Success -> {
                    Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_OK")
                    Result.success()
                }
                is DomainResult.Error -> {
                    Log.w(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_FAIL failure=${result.failure} willRetry=${runAttemptCount < 3}")
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
        } catch (e: CancellationException) {
            // Re-lanzar: CoroutineWorker depende de CancellationException para cancelar limpio.
            Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_CANCELLED — rethrow")
            throw e
        } catch (e: Exception) {
            Log.e(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_CRASH ex=${e::class.java.simpleName} msg=${e.message}", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
