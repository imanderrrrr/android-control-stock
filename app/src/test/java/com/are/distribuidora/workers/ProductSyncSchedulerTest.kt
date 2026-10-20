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
 * Frescura del stock entre teléfonos (2026-10-20): el periódico de productos baja de 6 h a 1 h.
 *
 * El periódico se encola en cada arranque de la app. Con la política KEEP, cambiar el intervalo en
 * el código no llegaba a los teléfonos ya instalados (seguían con el de 6 h para siempre); estos
 * tests usan WorkManager (versión de prueba, base en memoria) para comprobar lo que de verdad
 * queda programado. Ningún worker llega a correr: el planificador de prueba solo los ejecuta si
 * el test lo pide.
 */
@RunWith(RobolectricTestRunner::class)
class ProductSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private val uniqueName = SyncDebug.UNIQUE_WORK_NAME_PRODUCTS

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun activePeriodic(): WorkInfo =
        workManager.getWorkInfosForUniqueWork(uniqueName).get().single { !it.state.isFinished }

    /**
     * Estado de un teléfono que instaló la versión anterior: periódico de 6 h ya encolado, con la
     * misma restricción de red que el real (sin ella el planificador de prueba lo ejecuta, falla
     * y deja de contar como trabajo pendiente).
     */
    private fun enqueueLegacySixHourPeriodic() {
        val legacy = PeriodicWorkRequestBuilder<ProductSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            legacy,
        ).result.get()
    }

    @Test
    fun `un telefono que ya tenia el periodico de 6 h pasa a 1 h al actualizar la app`() {
        enqueueLegacySixHourPeriodic()

        ProductSyncScheduler(context).schedulePeriodic()

        assertEquals(
            TimeUnit.HOURS.toMillis(1),
            activePeriodic().periodicityInfo!!.repeatIntervalMillis,
        )
    }

    @Test
    fun `reabrir la app no reinicia ni duplica el periodico`() {
        enqueueLegacySixHourPeriodic()
        val scheduler = ProductSyncScheduler(context)
        scheduler.schedulePeriodic()
        val before = activePeriodic().id

        scheduler.schedulePeriodic()

        assertEquals(before, activePeriodic().id)
    }
}
