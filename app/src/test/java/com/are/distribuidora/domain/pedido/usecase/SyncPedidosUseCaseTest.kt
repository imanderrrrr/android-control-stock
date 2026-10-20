package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frescura del stock entre teléfonos (2026-10-20): cuando este teléfono sube una venta, el
 * servidor ya la descontó; se pide bajar el catálogo para traer también lo que vendieron los
 * demás teléfonos, en vez de esperar al periódico.
 */
class SyncPedidosUseCaseTest {

    private val online = object : ConnectivityChecker {
        override suspend fun isOnline() = true
    }
    private val offline = object : ConnectivityChecker {
        override suspend fun isOnline() = false
    }
    private val logger = object : Logger {
        override fun d(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private var refreshRequests = 0
    private val requestCatalogRefresh: () -> Unit = { refreshRequests++ }

    private fun pedido(id: String): PedidoWithItems = mockk(relaxed = true) {
        every { pedido.id } returns id
    }

    private fun repository(
        pendingCreate: List<PedidoWithItems> = emptyList(),
        pendingUpdate: List<PedidoWithItems> = emptyList(),
        failingIds: Set<String> = emptySet(),
    ): PedidoRepository = mockk(relaxed = true) {
        coEvery { getPendingPedidosForSync(any()) } returns pendingCreate
        coEvery { getPendingUpdatePedidosForSync(any()) } returns pendingUpdate
        coEvery { uploadAndMarkSynced(any()) } answers {
            if (firstArg<PedidoWithItems>().pedido.id in failingIds) throw RuntimeException("upload")
        }
        coEvery { updateAndMarkSynced(any()) } answers {
            if (firstArg<PedidoWithItems>().pedido.id in failingIds) throw RuntimeException("update")
        }
    }

    private fun useCase(repo: PedidoRepository, connectivity: ConnectivityChecker = online) =
        SyncPedidosUseCase(repo, connectivity, logger, requestCatalogRefresh)

    @Test
    fun `subir un pedido nuevo pide bajar el catalogo de productos`() = runBlocking {
        val result = useCase(repository(pendingCreate = listOf(pedido("p1"))))()

        assertTrue(result.isSuccess)
        assertEquals(1, refreshRequests)
    }

    @Test
    fun `subir la edicion de un pedido tambien pide bajar el catalogo`() = runBlocking {
        val result = useCase(repository(pendingUpdate = listOf(pedido("p1"))))()

        assertTrue(result.isSuccess)
        assertEquals(1, refreshRequests)
    }

    @Test
    fun `varios pedidos subidos piden una sola bajada`() = runBlocking {
        useCase(
            repository(
                pendingCreate = listOf(pedido("p1"), pedido("p2")),
                pendingUpdate = listOf(pedido("p3")),
            )
        )()

        assertEquals(1, refreshRequests)
    }

    @Test
    fun `si al menos un pedido sube aunque otro falle se pide la bajada`() = runBlocking {
        useCase(
            repository(
                pendingCreate = listOf(pedido("ok"), pedido("falla")),
                failingIds = setOf("falla"),
            )
        )()

        assertEquals(1, refreshRequests)
    }

    @Test
    fun `sin pedidos pendientes no se pide bajada`() = runBlocking {
        useCase(repository())()

        assertEquals(0, refreshRequests)
    }

    @Test
    fun `si fallan todos los pedidos no se pide bajada`() = runBlocking {
        val result = useCase(
            repository(pendingCreate = listOf(pedido("falla")), failingIds = setOf("falla"))
        )()

        assertTrue(result.isFailure)
        assertEquals(0, refreshRequests)
    }

    @Test
    fun `sin red no se sube nada ni se pide bajada`() = runBlocking {
        useCase(repository(pendingCreate = listOf(pedido("p1"))), connectivity = offline)()

        assertEquals(0, refreshRequests)
    }

    @Test
    fun `un fallo al pedir la bajada no convierte en fallo una subida que ya ocurrio`() = runBlocking {
        val result = SyncPedidosUseCase(
            repository(pendingCreate = listOf(pedido("p1"))),
            online,
            logger,
            requestCatalogRefresh = { throw IllegalStateException("WorkManager no inicializado") },
        )()

        assertTrue(result.isSuccess)
    }
}
