package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProductsUseCaseTest {

    // Antes esperaba fetchRemoteProducts/saveLocalProducts, que el use case dejó de llamar al
    // pasar a la bajada por lotes (syncDownstream): el test fallaba aunque el flujo era correcto.
    @Test
    fun `invoke sube los pendientes y luego baja el catálogo cuando hay conexión`() = runTest {
        val repo = FakeProductSyncRepository()
        val useCase = SyncProductsUseCase(repo, FakeConnectivityChecker(isOnline = true), FakeLogger())

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(listOf("upload", "downstream"), repo.calls)
    }

    @Test
    fun `invoke no sincroniza cuando no hay conexión`() = runTest {
        val repo = FakeProductSyncRepository()
        val useCase = SyncProductsUseCase(repo, FakeConnectivityChecker(isOnline = false), FakeLogger())

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<String>(), repo.calls)
    }

    // El periódico ("ProductSyncPeriodic") y el puntual ("ProductSyncNow") son trabajos únicos
    // distintos: pueden correr a la vez sobre el mismo use case @Singleton. El disparo puntual
    // llega porque algo cambió en el servidor DESPUÉS de que empezó la bajada en curso, así que
    // saltárselo pierde ese cambio hasta el siguiente disparo.
    @Test
    fun `una corrida que llega con otra en curso espera a que termine y vuelve a bajar`() = runTest {
        val firstDownstream = CompletableDeferred<Unit>()
        val repo = FakeProductSyncRepository(downstreamGates = mutableListOf(firstDownstream))
        val useCase = SyncProductsUseCase(repo, FakeConnectivityChecker(isOnline = true), FakeLogger())

        val periodic = async { useCase() }
        runCurrent()
        val oneTime = async { useCase() }
        runCurrent()

        firstDownstream.complete(Unit)

        assertTrue(periodic.await().isSuccess)
        assertTrue(oneTime.await().isSuccess)
        assertEquals(2, repo.downstreamCalls)
        assertEquals("las dos bajadas no deben solaparse", 1, repo.maxConcurrentDownstreams)
    }

    // ProductSyncScheduler.scheduleOneTimeNow encola con REPLACE: WorkManager cancela el worker
    // en curso y arranca el nuevo enseguida. La cancelación es asíncrona (el viejo puede estar
    // dentro de una transacción de Room, que no se interrumpe), así que el mutex sigue tomado
    // cuando el nuevo entra. Antes el nuevo se saltaba: se cancelaba una bajada y no corría otra.
    @Test
    fun `si REPLACE cancela la corrida en curso la nueva espera a que suelte el mutex y baja`() = runTest {
        val transactionInFlight = CompletableDeferred<Unit>()
        val repo = FakeProductSyncRepository(
            downstreamGates = mutableListOf(transactionInFlight),
            gateIsNonCancellable = true,
        )
        val useCase = SyncProductsUseCase(repo, FakeConnectivityChecker(isOnline = true), FakeLogger())

        val replaced = async { useCase() }
        runCurrent()
        replaced.cancel()
        val replacement = async { useCase() }
        runCurrent()

        transactionInFlight.complete(Unit)

        assertTrue(replacement.await().isSuccess)
        assertEquals(2, repo.downstreamCalls)
        assertEquals(listOf("upload", "downstream", "upload", "downstream"), repo.calls)
    }

    // Si llega un tercer disparo mientras el segundo espera, REPLACE cancela al que espera: no
    // debe llegar a bajar ni dejar el mutex tomado para los siguientes.
    @Test
    fun `una corrida cancelada mientras espera no baja y no bloquea a las siguientes`() = runTest {
        val firstDownstream = CompletableDeferred<Unit>()
        val repo = FakeProductSyncRepository(downstreamGates = mutableListOf(firstDownstream))
        val useCase = SyncProductsUseCase(repo, FakeConnectivityChecker(isOnline = true), FakeLogger())

        val running = async { useCase() }
        runCurrent()
        val waiting = async { useCase() }
        runCurrent()
        waiting.cancel()
        runCurrent()

        firstDownstream.complete(Unit)
        assertTrue(running.await().isSuccess)
        assertTrue(waiting.isCancelled)
        assertEquals(1, repo.downstreamCalls)

        val next = useCase()

        assertTrue(next.isSuccess)
        assertEquals(2, repo.downstreamCalls)
        assertFalse(repo.downstreamInFlight)
    }

    /**
     * [downstreamGates]: la n-ésima llamada a syncDownstream se suspende hasta que se complete
     * su gate (simula una bajada larga). Con [gateIsNonCancellable] la espera ignora la
     * cancelación, como una transacción de Room ya empezada.
     */
    private class FakeProductSyncRepository(
        private val downstreamGates: MutableList<CompletableDeferred<Unit>> = mutableListOf(),
        private val gateIsNonCancellable: Boolean = false,
    ) : ProductSyncRepository {
        val calls = mutableListOf<String>()
        var downstreamCalls: Int = 0
            private set
        var downstreamInFlight: Boolean = false
            private set
        var maxConcurrentDownstreams: Int = 0
            private set
        private var concurrentDownstreams = 0

        override suspend fun fetchRemoteProducts(): List<Product> = error("el use case ya no la usa")

        override suspend fun saveLocalProducts(products: List<Product>) = error("el use case ya no la usa")

        override suspend fun syncDownstream() {
            calls += "downstream"
            downstreamCalls++
            concurrentDownstreams++
            maxConcurrentDownstreams = maxOf(maxConcurrentDownstreams, concurrentDownstreams)
            downstreamInFlight = true
            try {
                val gate = downstreamGates.removeFirstOrNull() ?: return
                if (gateIsNonCancellable) withContext(NonCancellable) { gate.await() } else gate.await()
            } finally {
                concurrentDownstreams--
                downstreamInFlight = concurrentDownstreams > 0
            }
        }

        override suspend fun uploadPendingProducts() {
            calls += "upload"
        }
    }

    private class FakeConnectivityChecker(private val isOnline: Boolean) : ConnectivityChecker {
        override suspend fun isOnline(): Boolean = isOnline
    }

    private class FakeLogger : Logger {
        override fun d(tag: String, message: String) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }
}
