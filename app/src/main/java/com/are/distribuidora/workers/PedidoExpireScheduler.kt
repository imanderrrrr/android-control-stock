package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler para el worker de expiración de pedidos.
 *
 * Encola un [PedidoExpireWorker] periódico que se ejecuta una vez al día
 * con constraint de red CONNECTED. WorkManager garantiza que se ejecute
 * incluso si la app está cerrada, usando la política KEEP para no duplicar
 * trabajos si ya hay uno encolado.
 *
 * Debe llamarse una vez al arrancar la app (p.ej. en Application.onCreate).
 */
@Singleton
class PedidoExpireScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PedidoExpireWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        Log.d(TAG, "PedidoExpireScheduler: scheduled daily expiration job")
    }

    companion object {
        private const val TAG = "PedidoExpire"
        const val UNIQUE_WORK_NAME = "pedido_expire_daily"
    }
}

