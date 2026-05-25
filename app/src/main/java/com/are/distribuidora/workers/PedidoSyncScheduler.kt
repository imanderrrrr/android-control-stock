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

@Singleton
class PedidoSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getWorkManager() = WorkManager.getInstance(context)

    /**
     * Encola un trabajo único one-time con constraint de red CONNECTED.
     *
     * Política: KEEP — si ya existe un worker en ejecución o encolado para este
     * uniqueName, NO se cancela. Los nuevos pedidos confirmados mientras ese worker
     * corre serán recogidos por el mismo batch (BATCH_LIMIT) o por el siguiente
     * run natural (retry automático de WorkManager o el siguiente one-time disparado).
     *
     * Motivo del cambio desde REPLACE: REPLACE cancelaba el worker in-flight, lo que
     * podía dejar pedidos marcados como SYNCING sin que nadie los revirtiera a
     * PENDING_CREATE, causando bloqueo permanente de sincronización.
     *
     * @param reason Motivo del trigger (para logging / trazabilidad).
     */
    fun scheduleOneTimeSync(reason: String) {
        val runId = UUID.randomUUID().toString().take(8)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(SyncDebug.KEY_RUN_ID, runId)
            .putString("reason", reason)
            .build()

        val request = OneTimeWorkRequestBuilder<PedidoSyncWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        val uniqueName = SyncDebug.UNIQUE_WORK_NAME_PEDIDOS_ONE_TIME

        Log.d(
            SyncDebug.TAG_SYNC,
            "${SyncDebug.prefix(runId)} PEDIDO_ENQUEUE_NOW_START reason='$reason' uniqueName=$uniqueName policy=KEEP"
        )

        getWorkManager().enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

