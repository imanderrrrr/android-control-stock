package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.usecase.FakePedidoSyncRepository.State
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Subida de pedidos (local → Firestore): ningún pedido confirmado puede quedar varado.
 *
 * Caso real (2026-10): un pedido del 05/10 13:39 llegó al servidor el 06/10 10:16 porque una
 * ejecución sin red o con fallo terminaba en "éxito"/fallo definitivo y nadie la reencolaba.
 */
class SyncPedidosDrainQueueTest {

    private val repo = FakePedidoSyncRepository()
    private val connectivity = FakeConnectivityChecker(online = true)
    private val useCase = SyncPedidosUseCase(repo, connectivity, NoopLogger)

    @Test
    fun `sin red no sube nada y devuelve fallo para que se reintente`() = runTest {
        repo.addPendingCreate("p1")
        connectivity.online = false

        val result = useCase()

        assertTrue("sin red no puede contar como éxito", result.isFailure)
        assertEquals(emptyList<String>(), repo.uploads)
        assertEquals(listOf("p1"), repo.idsIn(State.PENDING_CREATE))
    }

    @Test
    fun `con 45 pedidos pendientes los sube todos en una sola ejecucion`() = runTest {
        val ids = (1..45).map { "p$it" }
        repo.addPendingCreate(*ids.toTypedArray())

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(ids, repo.idsIn(State.SYNCED))
    }

    @Test
    fun `con 25 ediciones pendientes las sube todas en una sola ejecucion`() = runTest {
        val ids = (1..25).map { "e$it" }
        repo.addPendingUpdate(*ids.toTypedArray())

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(ids, repo.idsIn(State.SYNCED))
    }

    @Test
    fun `un pedido que falla no frena a los demas, se intenta una vez y la ejecucion queda en fallo`() = runTest {
        repo.addPendingCreate("p1", "p2", "p3")
        repo.failWith["p2"] = IllegalStateException("rechazado")

        val result = useCase()

        assertTrue("un fallo parcial debe reintentarse, no contar como éxito", result.isFailure)
        assertEquals(listOf("p1", "p3"), repo.idsIn(State.SYNCED))
        assertEquals(listOf("p2"), repo.idsIn(State.PENDING_CREATE))
        assertEquals(1, repo.uploads.count { it == "create:p2" })
    }

    @Test
    fun `veinte fallos al frente de la cola no impiden subir los que vienen detras`() = runTest {
        val failing = (1..20).map { "f$it" }
        repo.addPendingCreate(*failing.toTypedArray())
        repo.addPendingCreate("ok1", "ok2", "ok3", "ok4", "ok5")
        failing.forEach { repo.failWith[it] = IllegalStateException("rechazado") }

        useCase()

        assertEquals(listOf("ok1", "ok2", "ok3", "ok4", "ok5"), repo.idsIn(State.SYNCED))
        assertEquals(failing, repo.idsIn(State.PENDING_CREATE))
    }

    @Test
    fun `un pedido confirmado mientras se sube otro entra en la misma ejecucion`() = runTest {
        repo.addPendingCreate("p1")
        repo.onUpload = { id -> if (id == "p1") repo.addPendingCreate("p2") }

        useCase()

        assertEquals(listOf("p1", "p2"), repo.idsIn(State.SYNCED))
    }

    @Test
    fun `un pedido confirmado mientras se suben ediciones entra en la misma ejecucion`() = runTest {
        repo.addPendingUpdate("e1")
        repo.onUpload = { id -> if (id == "e1") repo.addPendingCreate("p9") }

        useCase()

        assertEquals(listOf("e1", "p9"), repo.idsIn(State.SYNCED))
    }

    @Test
    fun `un pedido editado mientras se subia vuelve a subirse en la misma ejecucion`() = runTest {
        repo.addPendingUpdate("e1")
        var edited = false
        repo.onUpload = { id -> if (id == "e1" && !edited) { edited = true; repo.edit("e1") } }

        useCase()

        assertEquals(listOf("update:e1", "update:e1"), repo.uploads)
        assertEquals(listOf("e1"), repo.idsIn(State.SYNCED))
    }

    @Test
    fun `un pedido que sigue en la cola tras subirse sin cambios no se resube en la misma ejecucion`() = runTest {
        // Pasa si recoverStuckSyncingPedidos lo revierte a PENDING_CREATE en plena subida (el
        // markSynced condicional ya no lo encuentra en SYNCING), o con un repositorio mock estático.
        repo.addPendingCreate("p1")
        repo.onUpload = { id -> repo.states[id] = State.PENDING_CREATE }

        useCase()

        assertEquals(listOf("create:p1"), repo.uploads)
    }

    @Test
    fun `dos ejecuciones simultaneas no suben el mismo pedido dos veces`() = runTest {
        // El periódico de respaldo y el disparo al confirmar pueden coincidir en el tiempo.
        repo.addPendingCreate("p1", "p2", "p3")
        repo.onUpload = { yield() }

        listOf(async { useCase() }, async { useCase() }).awaitAll()

        assertEquals(listOf("create:p1", "create:p2", "create:p3"), repo.uploads)
        assertEquals(listOf("p1", "p2", "p3"), repo.idsIn(State.SYNCED))
    }
}
