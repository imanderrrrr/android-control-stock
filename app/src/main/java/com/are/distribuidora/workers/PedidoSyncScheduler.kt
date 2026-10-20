package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.are.distribuidora.core.sync.SyncDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PedidoSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getWorkManager() = WorkManager.getInstance(context)

    /**
     * Red de seguridad: periódico cada 15 min (el mínimo de WorkManager) con red CONNECTED.
     * Recoge cualquier pedido que el disparo inmediato no subió: reintentos agotados, proceso
     * matado, o una confirmación descartada por KEEP justo cuando otro worker terminaba.
     *
     * UPDATE, no KEEP: conserva el id y el temporizador (llamarlo en cada arranque no lo reinicia)
     * pero aplica la programación actual; con KEEP un cambio de intervalo o de constraints nunca
     * llegaría a los teléfonos que ya lo tienen. Usa otro uniqueName que el one-time, así que
     * pueden coincidir; [SyncPedidosUseCase] serializa ambas ejecuciones.
     * Sin runId en el input: cada ejecución genera el suyo.
     */
    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PedidoSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString("reason", "PERIODIC").build())
            .build()

        getWorkManager().enqueueUniquePeriodicWork(
            SyncDebug.UNIQUE_WORK_NAME_PEDIDOS_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        Log.d(
            SyncDebug.TAG_SYNC,
            "PEDIDO_SCHEDULE_PERIODIC_OK uniqueName=${SyncDebug.UNIQUE_WORK_NAME_PEDIDOS_PERIODIC} interval=15m policy=UPDATE"
        )
    }

    /**
     * Encola un trabajo único one-time con constraint de red CONNECTED.
     *
     * Política: KEEP — si ya existe un worker en ejecución o encolado para este
     * uniqueName, NO se cancela. Los nuevos pedidos confirmados mientras ese worker
     * corre los recoge esa misma ejecución ([SyncPedidosUseCase] vuelve a consultar la
     * cola hasta vaciarla) o, si llegan justo al terminar, el periódico de [schedulePeriodic].
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

