package com.are.distribuidora.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.are.distribuidora.domain.pedido.usecase.FakeConnectivityChecker
import com.are.distribuidora.domain.pedido.usecase.FakePedidoSyncRepository
import com.are.distribuidora.domain.pedido.usecase.NoopLogger
import com.are.distribuidora.domain.pedido.usecase.SyncPedidosUseCase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Decisión success / retry / failure del worker de subida de pedidos, con el caso de uso real
 * sobre una cola en memoria. Un `failure()` deja los pedidos sin subir hasta el siguiente disparo,
 * así que solo es aceptable para errores que reintentar no arregla.
 */
@RunWith(RobolectricTestRunner::class)
class PedidoSyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repo = FakePedidoSyncRepository()
    private val connectivity = FakeConnectivityChecker(online = true)

    private fun runWorker(runAttemptCount: Int = 0): ListenableWorker.Result = runBlocking {
        val useCase = SyncPedidosUseCase(repo, connectivity, NoopLogger)
        TestListenableWorkerBuilder<PedidoSyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = PedidoSyncWorker(appContext, workerParameters, useCase, NoopLogger)
            })
            .build()
            .doWork()
    }

    private fun unavailable() = FirebaseFirestoreException("Failed to get document because the client is offline.", Code.UNAVAILABLE)

    @Test
    fun `cola vacia termina en success`() {
        assertEquals(ListenableWorker.Result.success(), runWorker())
    }

    @Test
    fun `sin red pide reintento`() {
        repo.addPendingCreate("p1")
        connectivity.online = false

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `si todos los pedidos fallan con UNAVAILABLE pide reintento`() {
        repo.addPendingCreate("p1", "p2")
        repo.failWith["p1"] = unavailable()
        repo.failWith["p2"] = unavailable()

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `si uno sube y otro falla con UNAVAILABLE pide reintento para el que quedo`() {
        repo.addPendingCreate("p1", "p2")
        repo.failWith["p2"] = unavailable()

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `un UNAVAILABLE envuelto en otra excepcion tambien pide reintento`() {
        repo.addPendingCreate("p1")
        repo.failWith["p1"] = IllegalStateException("upload falló", unavailable())

        assertEquals(ListenableWorker.Result.retry(), runWorker())
    }

    @Test
    fun `un error que reintentar no arregla termina en failure`() {
        repo.addPendingCreate("p1")
        repo.failWith["p1"] = FirebaseFirestoreException("Missing or insufficient permissions.", Code.PERMISSION_DENIED)

        assertEquals(ListenableWorker.Result.failure(), runWorker())
    }

    @Test
    fun `los reintentos tienen tope y luego el pedido queda para el periodico`() {
        // Sin tope, el backoff exponencial llega a 5 h y, con KEEP, ese trabajo en espera
        // absorbería los disparos de las confirmaciones siguientes.
        repo.addPendingCreate("p1")
        repo.failWith["p1"] = unavailable()

        assertEquals(ListenableWorker.Result.failure(), runWorker(runAttemptCount = 10))
    }
}
