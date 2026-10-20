package com.are.distribuidora.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.are.distribuidora.core.sync.SyncDebug
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Red de seguridad de la subida de pedidos: un trabajo periódico que recoge cualquier pedido
 * que se haya quedado sin subir (fallo definitivo, proceso matado, disparo descartado por KEEP).
 */
@RunWith(RobolectricTestRunner::class)
class PedidoSyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: PedidoSyncScheduler

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        scheduler = PedidoSyncScheduler(context)
    }

    private fun periodicWork(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosByTag(PedidoSyncWorker::class.java.name).get()
            .filter { it.periodicityInfo != null }

    @Test
    fun `el periodico de respaldo sube pendientes cada 15 minutos cuando hay red`() {
        scheduler.schedulePeriodic()

        val work = periodicWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(TimeUnit.MINUTES.toMillis(15), work.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `programarlo en cada arranque no reinicia ni duplica el periodico`() {
        // Si cada arranque reiniciara el temporizador, abrir la app a menudo impediría que corra.
        scheduler.schedulePeriodic()
        val first = periodicWork().single()
        Thread.sleep(20)

        scheduler.schedulePeriodic()

        val again = periodicWork().single()
        assertEquals(first.id, again.id)
        assertEquals(first.nextScheduleTimeMillis, again.nextScheduleTimeMillis)
    }

    @Test
    fun `un periodico programado por una version anterior pasa a la programacion actual`() {
        // Con KEEP, un cambio de intervalo nunca llegaría a los teléfonos que ya lo tienen.
        val older = PeriodicWorkRequestBuilder<PedidoSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncDebug.UNIQUE_WORK_NAME_PEDIDOS_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, older,
        ).result.get()

        scheduler.schedulePeriodic()

        val work = periodicWork().single()
        assertEquals(older.id, work.id)
        assertEquals(TimeUnit.MINUTES.toMillis(15), work.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }
}
