package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.are.distribuidora.core.sync.SyncDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encola la subida de ediciones de pedidos ajenos (sistema `orders`).
 *
 * Política APPEND_OR_REPLACE: si ya hay un worker en curso, encola otro DETRÁS para que
 * recoja ediciones confirmadas mientras el primero corría (evita que un pedido editado
 * quede sin subir hasta el próximo arranque). El worker es idempotente: relee la cola.
 */
@Singleton
class OrdersUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueueUploadWorker(reason: String = "ORDER_EDITED") {
        val runId = UUID.randomUUID().toString().take(8)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(SyncDebug.KEY_RUN_ID, runId)
            .putString("reason", reason)
            .build()

        val request = OneTimeWorkRequestBuilder<OrdersUploadWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        Log.d(
            SyncDebug.TAG_SYNC,
            "${SyncDebug.prefix(runId)} ORDERS_UPLOAD_ENQUEUE reason='$reason' policy=APPEND_OR_REPLACE",
        )

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncDebug.UNIQUE_WORK_NAME_ORDERS_UPLOAD_ONE_TIME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
