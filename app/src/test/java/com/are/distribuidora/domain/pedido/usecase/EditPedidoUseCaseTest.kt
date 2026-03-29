package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.usecase.ValidateOrderLimitUseCase
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoItemInput
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.PreviousItemSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [EditPedidoUseCase].
 *
 * Cubre:
 * 1. Ã‰xito: guardado atÃ³mico llama al repo con los parÃ¡metros correctos.
 * 2. Error: lista de Ã­tems vacÃ­a â†’ ValidationError.
 * 3. Error: cantidad = 0 â†’ ValidationError.
 * 4. Error: total negativo (descuento global excede subtotal) â†’ ValidationError.
 * 5. Ã‰xito con soft-delete: Ã­tems en itemIdsToDelete se pasan al repo correctamente.
 * 6. Ã‰xito con Ã­tem nuevo (itemId == null): el usecase lo pasa tal cual al repo.
 * 7. PropagaciÃ³n de error del repo (DatabaseError / NotFound).
 */
class EditPedidoUseCaseTest {

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Fake repo
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Fake completo de [PedidoRepository] que implementa todos los mÃ©todos
     * (incluidos los nuevos de ediciÃ³n) con stubs mÃ­nimos.
     *
     * [editResult]     â€” lo que devuelve editPedido().
     * [pedidoExists]   â€” true â†’ getById encuentra el pedido; false â†’ editPedido devuelve NotFound.
     */
    private class FakePedidoRepository(
        private val editResult: Result<Unit> = Result.Success(Unit),
        private val pedidoExists: Boolean = true,
    ) : PedidoRepository {

        /** Captura los parÃ¡metros con los que se llamÃ³ editPedido(). */
        var capturedEditParams: EditPedidoParams? = null

        // â”€â”€ MÃ©todos de ediciÃ³n â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        override suspend fun editPedido(params: EditPedidoParams): Result<Unit> {
            capturedEditParams = params
            return if (!pedidoExists) Result.Error(Failure.NotFound) else editResult
        }

        override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit

        override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> = emptyFlow()

        override suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()

        // â”€â”€ Stubs obligatorios de la interfaz â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

        override suspend fun createPedido(params: CreatePedidoParams): Result<String> = Result.Success("id")
        override suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>> = Result.Success(emptyList())
        override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = null
        override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = null
        override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> = emptyList()
        override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> = emptyFlow()
        override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> = emptyFlow()
        override suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()
        override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun recoverStuckSyncingPedidos() = Unit
        override suspend fun deletePedido(pedidoId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = Result.Success(Unit)
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Fake ClientRepository (sin lÃ­mite de compra por defecto)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private class FakeClientRepository(
        private val client: Client? = null,
    ) : ClientRepository {
        override suspend fun create(client: Client) = Result.Success(Unit)
        override suspend fun insert(client: Client) = Unit
        override suspend fun getClients(limit: Int) = Result.Success(emptyList<Client>())
        override suspend fun getClientById(id: String): Result<Client?> = Result.Success(client)
        override suspend fun getByRouteId(routeId: String, limit: Int) = Result.Success(emptyList<Client>())
        override fun observeByRouteId(routeId: String, limit: Int): Flow<List<Client>> = emptyFlow()
        override suspend fun searchClients(query: String, limit: Int) = Result.Success(emptyList<Client>())
        override suspend fun searchClientsByRoute(routeId: String, query: String) = Result.Success(emptyList<Client>())
        override suspend fun delete(id: String) = Result.Success(Unit)
        override suspend fun syncClients(limit: Int) = Result.Success(Unit)
        override suspend fun update(client: Client) = Result.Success(Unit)
    }

    /** Helper para construir el UseCase con fakes inyectados. */
    private fun buildUseCase(
        repo: FakePedidoRepository = FakePedidoRepository(),
        clientRepo: FakeClientRepository = FakeClientRepository(),
    ) = EditPedidoUseCase(repo, clientRepo, ValidateOrderLimitUseCase())

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Helpers de construcciÃ³n de datos de prueba
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun itemInput(
        itemId: String? = "item-1",
        productoId: String = "prod-1",
        nombre: String = "Producto A",
        precio: Double = 10.0,
        cantidad: Int = 2,
        descuento: Double = 0.0,
    ) = EditPedidoItemInput(
        itemId         = itemId,
        productoId     = productoId,
        nombre         = nombre,
        precioUnitario = precio,
        cantidad       = cantidad,
        descuentoItem  = descuento,
    )

    private fun editParams(
        pedidoId: String = "pedido-1",
        vendedorId: String = "vendedor-1",
        clienteId: String? = null,
        items: List<EditPedidoItemInput> = listOf(itemInput()),
        itemIdsToDelete: List<String> = emptyList(),
        previousItems: List<PreviousItemSnapshot> = emptyList(),
        descuentoGlobal: Double = 0.0,
    ) = EditPedidoParams(
        pedidoId        = pedidoId,
        vendedorId      = vendedorId,
        clienteId       = clienteId,
        itemsToUpsert   = items,
        itemIdsToDelete = itemIdsToDelete,
        previousItems   = previousItems,
        descuentoGlobal = descuentoGlobal,
    )

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Tests
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // â”€â”€ 1. Happy path: ediciÃ³n exitosa â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `edicion exitosa devuelve Success y llama al repo con los parametros correctos`() = runBlocking {
        val repo   = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        val params = editParams(
            pedidoId  = "pedido-42",
            vendedorId = "v-99",
            items     = listOf(
                itemInput(itemId = "item-A", productoId = "prod-1", precio = 25.0, cantidad = 3),
                itemInput(itemId = "item-B", productoId = "prod-2", precio = 10.0, cantidad = 1),
            ),
            descuentoGlobal = 5.0,
        )

        val result = useCase(params)

        // El use case retorna Success
        assertTrue("Esperaba Result.Success pero fue: $result", result is Result.Success)

        // El repo recibiÃ³ exactamente los mismos parÃ¡metros
        val captured = repo.capturedEditParams
        assertNotNull("editPedido() en el repo nunca fue llamado", captured)
        assertEquals("pedido-42",  captured!!.pedidoId)
        assertEquals("v-99",       captured.vendedorId)
        assertEquals(2,            captured.itemsToUpsert.size)
        assertEquals(5.0,          captured.descuentoGlobal, 0.0)
    }

    // â”€â”€ 2. Items vacÃ­os â†’ ValidationError â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `items vacios devuelve ValidationError y NO llama al repo`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        val result = useCase(editParams(items = emptyList()))

        assertTrue(result is Result.Error)
        val failure = (result as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals(
            "El pedido debe tener al menos un Ã­tem",
            (failure as Failure.ValidationError).message,
        )
        // El repo no debe haber sido llamado
        assertNull("No deberÃ­a haber llamado al repo", repo.capturedEditParams)
    }

    // â”€â”€ 3. Cantidad = 0 â†’ ValidationError â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `item con cantidad cero devuelve ValidationError y NO llama al repo`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        val result = useCase(editParams(items = listOf(itemInput(cantidad = 0))))

        assertTrue(result is Result.Error)
        val failure = (result as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals(
            "La cantidad de cada Ã­tem debe ser mayor a 0",
            (failure as Failure.ValidationError).message,
        )
        assertNull(repo.capturedEditParams)
    }

    // â”€â”€ 4. Total negativo â†’ ValidationError â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `descuento global mayor al subtotal devuelve ValidationError`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        // subtotal = 10 * 1 = 10, descuentoGlobal = 20 â†’ total = -10
        val result = useCase(editParams(
            items           = listOf(itemInput(precio = 10.0, cantidad = 1)),
            descuentoGlobal = 20.0,
        ))

        assertTrue(result is Result.Error)
        val failure = (result as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals(
            "El total del pedido no puede ser negativo",
            (failure as Failure.ValidationError).message,
        )
        assertNull(repo.capturedEditParams)
    }

    // â”€â”€ 5. Soft-delete: itemIdsToDelete llega correctamente al repo â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `items a eliminar se pasan al repo en itemIdsToDelete`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        val result = useCase(editParams(
            items           = listOf(itemInput(itemId = "item-nuevo")),
            itemIdsToDelete = listOf("item-viejo-1", "item-viejo-2"),
        ))

        assertTrue(result is Result.Success)
        val captured = repo.capturedEditParams!!
        assertEquals(listOf("item-viejo-1", "item-viejo-2"), captured.itemIdsToDelete)
    }

    // â”€â”€ 6. Ãtem nuevo (itemId == null) pasa validaciÃ³n y llega al repo â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `item nuevo con itemId null es valido y llega al repo`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        val nuevoItem = itemInput(itemId = null, productoId = "prod-99", cantidad = 5)
        val result = useCase(editParams(items = listOf(nuevoItem)))

        assertTrue(result is Result.Success)
        val captured = repo.capturedEditParams!!
        assertEquals(1, captured.itemsToUpsert.size)
        assertNull("itemId de Ã­tem nuevo debe ser null", captured.itemsToUpsert[0].itemId)
        assertEquals("prod-99", captured.itemsToUpsert[0].productoId)
        assertEquals(5, captured.itemsToUpsert[0].cantidad)
    }

    // â”€â”€ 7a. El repo devuelve DatabaseError â†’ se propaga â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `cuando el repo falla con DatabaseError se propaga el error`() = runBlocking {
        val repo    = FakePedidoRepository(editResult = Result.Error(Failure.DatabaseError))
        val useCase = buildUseCase(repo)

        val result = useCase(editParams())

        assertTrue(result is Result.Error)
        assertEquals(Failure.DatabaseError, (result as Result.Error).failure)
    }

    // â”€â”€ 7b. El repo devuelve NotFound â†’ se propaga â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `cuando el pedido no existe el repo devuelve NotFound y se propaga`() = runBlocking {
        val repo    = FakePedidoRepository(pedidoExists = false)
        val useCase = buildUseCase(repo)

        val result = useCase(editParams())

        assertTrue(result is Result.Error)
        assertEquals(Failure.NotFound, (result as Result.Error).failure)
    }

    // â”€â”€ 8. CÃ¡lculo de totales: el usecase valida el total correcto â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `calculo de total es correcto con descuentos por item y descuento global`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        // item1: (15 * 4) - 10 = 50
        // item2: (8  * 3) -  4 = 20
        // subtotal = 70, descuentoGlobal = 5, total = 65 (positivo â†’ OK)
        val result = useCase(editParams(
            items = listOf(
                itemInput(itemId = "i1", precio = 15.0, cantidad = 4, descuento = 10.0),
                itemInput(itemId = "i2", productoId = "prod-2", precio = 8.0, cantidad = 3, descuento = 4.0),
            ),
            descuentoGlobal = 5.0,
        ))

        assertTrue("El total calculado deberÃ­a ser positivo â†’ Success", result is Result.Success)
        // Los parÃ¡metros llegan intactos al repo (el usecase no modifica los Ã­tems)
        val captured = repo.capturedEditParams!!
        assertEquals(2,   captured.itemsToUpsert.size)
        assertEquals(5.0, captured.descuentoGlobal, 0.0)
    }

    // â”€â”€ 9. Un solo Ã­tem vÃ¡lido con descuento exactamente igual al subtotalBase â”€

    @Test
    fun `total exactamente cero con descuento igual al subtotal es valido`() = runBlocking {
        val repo    = FakePedidoRepository()
        val useCase = buildUseCase(repo)

        // item: (10 * 2) - 0 = 20, descuentoGlobal = 20 â†’ total = 0 (vÃ¡lido, no negativo)
        val result = useCase(editParams(
            items           = listOf(itemInput(precio = 10.0, cantidad = 2)),
            descuentoGlobal = 20.0,
        ))

        assertTrue("Total = 0 es vÃ¡lido (no negativo)", result is Result.Success)
    }
}
