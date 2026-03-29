package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.usecase.ValidateOrderLimitUseCase
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePedidoUseCaseTest {

    /**
     * @param existingOrderForClienteToday  ID del pedido ya existente HOY para el cliente,
     *        o null si no hay ninguno. Controla [findActivePedidoByClienteAndDay].
     */
    private class FakePedidoRepository(
        private val createResult: Result<String> = Result.Success("pedido-generated-id"),
        private val existingOrderForClienteToday: String? = null,
    ) : PedidoRepository {
        var lastParams: CreatePedidoParams? = null

        override suspend fun createPedido(params: CreatePedidoParams): Result<String> {
            lastParams = params
            return createResult
        }
        override suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>> = Result.Success(emptyList())
        override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = null
        override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = existingOrderForClienteToday
        override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> = emptyList()
        override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> = emptyFlow()
        override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> = emptyFlow()
        override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> = emptyFlow()
        override suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()
        override suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()
        override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun editPedido(params: com.are.distribuidora.domain.pedido.model.EditPedidoParams): Result<Unit> = Result.Success(Unit)
        override suspend fun recoverStuckSyncingPedidos() = Unit
        override suspend fun deletePedido(pedidoId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = Result.Success(Unit)
    }

    private class FakeClientRepository(
        private val clientResult: Result<Client?> = Result.Success(sampleClient()),
    ) : ClientRepository {
        override suspend fun create(client: Client): Result<Unit> = Result.Success(Unit)
        override suspend fun insert(client: Client) = Unit
        override suspend fun getClients(limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun getClientById(id: String): Result<Client?> = clientResult
        override suspend fun getByRouteId(routeId: String, limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override fun observeByRouteId(routeId: String, limit: Int): Flow<List<Client>> = emptyFlow()
        override suspend fun searchClients(query: String, limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun searchClientsByRoute(routeId: String, query: String): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun syncClients(limit: Int): Result<Unit> = Result.Success(Unit)
        override suspend fun update(client: Client): Result<Unit> = Result.Success(Unit)
    }

    companion object {
        fun sampleClient() = Client(
            id = "cliente-1", name = "Maria Lopez", phone = "55551234", address = "Zona 5",
            latitude = null, longitude = null, maxOrderAmountInCents = null,
            isActive = true, isDeleted = false, routeId = "ruta-1",
            syncState = SyncState.SYNCED,
            createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
            createdBy = "vendedor-1", lastModifiedBy = "vendedor-1",
        )

        fun sampleItem(
            productoId: String = "prod-1", nombre: String = "Producto A",
            precio: Double = 10.0, cantidad: Int = 2, descuento: Double = 0.0,
        ) = CreatePedidoItemInput(
            productoId = productoId, nombre = nombre,
            precioUnitario = precio, cantidad = cantidad, descuentoItem = descuento,
        )
    }

    // â”€â”€ Validaciones â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `lista de items vacia retorna error de validacion`() = runBlocking {
        val useCase = CreatePedidoUseCase(FakePedidoRepository(), FakeClientRepository(), ValidateOrderLimitUseCase())
        val resultado = useCase(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Pedro", null, null)),
            descuentoGlobal = 0.0, items = emptyList(),
        )
        assertTrue(resultado is Result.Error)
        val failure = (resultado as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals("El pedido debe tener al menos un Ã­tem", (failure as Failure.ValidationError).message)
    }

    @Test
    fun `item con cantidad 0 retorna error de validacion`() = runBlocking {
        val useCase = CreatePedidoUseCase(FakePedidoRepository(), FakeClientRepository(), ValidateOrderLimitUseCase())
        val resultado = useCase(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Pedro", null, null)),
            descuentoGlobal = 0.0, items = listOf(sampleItem(cantidad = 0)),
        )
        assertTrue(resultado is Result.Error)
        assertEquals(
            "La cantidad de los Ã­tems debe ser mayor a 0",
            ((resultado as Result.Error).failure as Failure.ValidationError).message
        )
    }

    @Test
    fun `descuento global mayor al subtotal retorna error de validacion`() = runBlocking {
        val useCase = CreatePedidoUseCase(FakePedidoRepository(), FakeClientRepository(), ValidateOrderLimitUseCase())
        val resultado = useCase(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Pedro", null, null)),
            descuentoGlobal = 50.0, items = listOf(sampleItem(precio = 10.0, cantidad = 1)),
        )
        assertTrue(resultado is Result.Error)
        assertEquals(
            "El total del pedido no puede ser negativo",
            ((resultado as Result.Error).failure as Failure.ValidationError).message
        )
    }

    @Test
    fun `cliente temporal con nombre en blanco retorna error de validacion`() = runBlocking {
        val useCase = CreatePedidoUseCase(FakePedidoRepository(), FakeClientRepository(), ValidateOrderLimitUseCase())
        val resultado = useCase(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("", null, null)),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Error)
        assertEquals(
            "El nombre del cliente temporal es obligatorio",
            ((resultado as Result.Error).failure as Failure.ValidationError).message
        )
    }

    @Test
    fun `cliente existente no encontrado retorna error`() = runBlocking {
        val useCase = CreatePedidoUseCase(
            FakePedidoRepository(),
            FakeClientRepository(clientResult = Result.Success(null)),
            ValidateOrderLimitUseCase(),
        )
        val resultado = useCase(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Existente("inexistente"),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Error)
    }

    // â”€â”€ Camino feliz â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `pedido con cliente temporal valido delega al repositorio`() = runBlocking {
        val repo = FakePedidoRepository()
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "vendedor-1", routeId = "ruta-1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Pedro Garcia", "12345", "Calle 2")),
            descuentoGlobal = 0.0, items = listOf(sampleItem(precio = 25.0, cantidad = 2)),
        )
        assertTrue(resultado is Result.Success<*>)
        assertEquals("pedido-generated-id", (resultado as Result.Success<String>).value)
        assertEquals("vendedor-1", repo.lastParams!!.vendedorId)
        assertEquals("Pedro Garcia", repo.lastParams!!.clienteSnapshot.nombre)
    }

    @Test
    fun `pedido con cliente existente usa snapshot del cliente`() = runBlocking {
        val repo = FakePedidoRepository()
        CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "vendedor-1", routeId = "ruta-1",
            cliente = ClienteSelection.Existente("cliente-1"),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertEquals("Maria Lopez", repo.lastParams!!.clienteSnapshot.nombre)
        assertEquals("cliente-1", repo.lastParams!!.clienteId)
    }

    @Test
    fun `fallo en el repositorio se propaga como error`() = runBlocking {
        val repo = FakePedidoRepository(createResult = Result.Error(Failure.DatabaseError))
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "v1", routeId = "r1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Ana", null, null)),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Error)
        assertEquals(Failure.DatabaseError, (resultado as Result.Error).failure)
    }

    // â”€â”€ Regla de negocio: 1 pedido por cliente por dÃ­a â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    fun `cliente existente con pedido activo el mismo dia retorna DuplicateOrder`() = runBlocking {
        // El repositorio indica que YA hay un pedido para este cliente hoy
        val repo = FakePedidoRepository(existingOrderForClienteToday = "pedido-existente-123")
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "vendedor-1", routeId = "ruta-1", deliveryDate = "2026-02-24",
            cliente = ClienteSelection.Existente("cliente-1"),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Error)
        val failure = (resultado as Result.Error).failure
        assertTrue(failure is Failure.DuplicateOrder)
        assertEquals("pedido-existente-123", (failure as Failure.DuplicateOrder).existingOrderId)
    }

    @Test
    fun `cliente existente sin pedido activo hoy crea pedido normalmente`() = runBlocking {
        // El repositorio indica que NO hay pedido para este cliente hoy
        val repo = FakePedidoRepository(existingOrderForClienteToday = null)
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "vendedor-1", routeId = "ruta-1", deliveryDate = "2026-02-24",
            cliente = ClienteSelection.Existente("cliente-1"),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Success<*>)
    }

    @Test
    fun `cliente temporal no aplica regla de 1 pedido por dia`() = runBlocking {
        // Incluso si existingOrderForClienteToday != null, el cliente temporal no es bloqueado
        val repo = FakePedidoRepository(existingOrderForClienteToday = "pedido-existente-123")
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "v1", routeId = "r1", deliveryDate = "2026-02-24",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Pedro", null, null)),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue("Cliente temporal siempre pasa sin importar duplicados", resultado is Result.Success<*>)
    }

    @Test
    fun `mismo cliente ruta diferente mismo dia sigue bloqueado por regla dia`() = runBlocking {
        // La nueva regla bloquea por cliente+dÃ­a sin importar la ruta ni el vendedor
        val repo = FakePedidoRepository(existingOrderForClienteToday = "pedido-ruta-anterior")
        val resultado = CreatePedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase())(
            vendedorId = "vendedor-1", routeId = "ruta-2",  // ruta diferente
            cliente = ClienteSelection.Existente("cliente-1"),
            descuentoGlobal = 0.0, items = listOf(sampleItem()),
        )
        assertTrue(resultado is Result.Error)
        assertTrue((resultado as Result.Error).failure is Failure.DuplicateOrder)
    }

    // â”€â”€ Tests OrderKeyUtil (sin cambios, la utilidad sigue existiendo para el sync con Firestore) â”€â”€â”€

    @Test
    fun `misma entrada produce el mismo hash deterministico`() {
        val k1 = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-X", "2026-02-21", "cliente-99", "vendedor-A")
        val k2 = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-X", "2026-02-21", "cliente-99", "vendedor-A")
        assertEquals(k1, k2)
    }

    @Test
    fun `entradas distintas producen hashes distintos`() {
        val k1 = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-X", "2026-02-21", "cliente-99", "vendedor-A")
        val k2 = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-X", "2026-02-22", "cliente-99", "vendedor-A")
        assertTrue(k1 != k2)
    }

    @Test
    fun `clientId vacio retorna null`() {
        val key = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-X", "2026-02-21", "", "vendedor-A")
        assertEquals(null, key)
    }

    @Test
    fun `orderKey tiene 64 caracteres hex lowercase`() {
        val key = com.are.distribuidora.core.utils.OrderKeyUtil.compute("ruta-1", "2026-02-21", "cliente-1", "vendedor-1")!!
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]+")))
    }
}
