package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.usecase.ExpirePedidosUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Worker periódico que expira y borra pedidos con ≥ 14 días de antigüedad.
 *
 * Se ejecuta una vez al día con constraint de red CONNECTED (para poder
 * realizar el soft/hard delete en Firestore). Si no hay red, WorkManager
 * reintentará automáticamente cuando la conectividad se restaure.
 *
 * El procesamiento real está en [ExpirePedidosUseCase]; este worker solo
 * actúa como punto de entrada de la capa de infraestructura.
 */
@HiltWorker
class PedidoExpireWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val expirePedidosUseCase: ExpirePedidosUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "PedidoExpireWorker: started attempt=$runAttemptCount")

        return try {
            val result = expirePedidosUseCase()

            if (result.isSuccess()) {
                Log.d(TAG, "PedidoExpireWorker: finished OK")
                Result.success()
            } else {
                Log.w(TAG, "PedidoExpireWorker: finished with errors, retry=${runAttemptCount < MAX_ATTEMPTS}")
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "PedidoExpireWorker: cancelled — rethrowing")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PedidoExpireWorker: unexpected crash", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PedidoExpire"
        private const val MAX_ATTEMPTS = 3
    }
}



