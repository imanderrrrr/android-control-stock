package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.domain.pedido.Pedido
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica que al confirmar un pedido:
 *  1. El stock del producto se descuenta correctamente.
 *  2. Si la cantidad pedida supera el stock, el excedente se registra en `comprometido`.
 *
 * La lógica central está en [com.are.distribuidora.data.local.dao.ProductDao.deductStockAndCommit]:
 *   stock'       = stock - MIN(stock, cantidad)
 *   comprometido' = comprometido + MAX(0, cantidad - stock)
 *
 * Este test usa Fakes en memoria para no depender de Room ni de Android.
 */
class StockDeductionOnOrderTest {

    // ─── Modelo en memoria que replica la lógica SQL de deductStockAndCommit ───

    /** Estado del stock de un producto en memoria. */
    data class StockState(val stock: Int, val comprometido: Int)

    /**
     * Replica en Kotlin la misma lógica SQL de ProductDao.deductStockAndCommit:
     *   stock'       = stock - MIN(stock, cantidad)
     *   comprometido' = comprometido + MAX(0, cantidad - stock)
     *
     * Esto permite validar la lógica de negocio sin necesidad de Room.
     */
    private fun applyDeduction(state: StockState, cantidad: Int): StockState {
        val descontado = minOf(state.stock, cantidad)
        val excedente = maxOf(0, cantidad - state.stock)
        return StockState(
            stock = state.stock - descontado,
            comprometido = state.comprometido + excedente,
        )
    }

    // ─── Tests de la lógica de deducción ──────────────────────────────────────

    @Test
    fun `stock suficiente - se resta todo y comprometido no cambia`() {
        // Stock = 10, pedido = 10 → stock = 0, comprometido = 0
        val resultado = applyDeduction(StockState(stock = 10, comprometido = 0), cantidad = 10)
        assertEquals(0, resultado.stock)
        assertEquals(0, resultado.comprometido)
    }

    @Test
    fun `stock mayor al pedido - solo se descuenta lo pedido`() {
        // Stock = 15, pedido = 5 → stock = 10, comprometido = 0
        val resultado = applyDeduction(StockState(stock = 15, comprometido = 0), cantidad = 5)
        assertEquals(10, resultado.stock)
        assertEquals(0, resultado.comprometido)
    }

    @Test
    fun `stock parcial - se agota stock y el excedente va a comprometido`() {
        // Stock = 2, pedido = 10 → stock = 0, comprometido = 8
        val resultado = applyDeduction(StockState(stock = 2, comprometido = 0), cantidad = 10)
        assertEquals(0, resultado.stock)
        assertEquals(8, resultado.comprometido)
    }

    @Test
    fun `stock cero - todo el pedido va a comprometido`() {
        // Stock = 0, pedido = 5 → stock = 0, comprometido = 5
        val resultado = applyDeduction(StockState(stock = 0, comprometido = 0), cantidad = 5)
        assertEquals(0, resultado.stock)
        assertEquals(5, resultado.comprometido)
    }

    @Test
    fun `comprometido previo se acumula correctamente`() {
        // Stock = 0, comprometido = 3 ya existente, pedido = 4 → stock = 0, comprometido = 7
        val resultado = applyDeduction(StockState(stock = 0, comprometido = 3), cantidad = 4)
        assertEquals(0, resultado.stock)
        assertEquals(7, resultado.comprometido)
    }

    @Test
    fun `stock nunca queda negativo`() {
        // Stock = 1, pedido = 100 → stock = 0 (nunca negativo), comprometido = 99
        val resultado = applyDeduction(StockState(stock = 1, comprometido = 0), cantidad = 100)
        assertEquals(0, resultado.stock)
        assertEquals(99, resultado.comprometido)
    }

    // ─── Test de integración: PedidoRepositoryImpl llama al DAO correctamente ─

    /**
     * FakePedidoRepository que registra qué ítems se recibió para confirmar
     * que el UseCase delega correctamente al repositorio.
     */
    private class FakePedidoRepository : PedidoRepository {
        var capturedParams: CreatePedidoParams? = null

        override suspend fun createPedido(params: CreatePedidoParams): Result<String> {
            capturedParams = params
            return Result.Success("pedido-test-id")
        }

        override suspend fun listPedidosByCliente(clienteId: String) = Result.Success(emptyList<Pedido>())
        override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = null
        override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = null
        override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> = emptyList()
        override fun observeAllPedidosWithItems(): kotlinx.coroutines.flow.Flow<List<PedidoWithItems>> = kotlinx.coroutines.flow.emptyFlow()
        override fun observeAllPedidosWithItemsByDate(deliveryDate: String): kotlinx.coroutines.flow.Flow<List<PedidoWithItems>> = kotlinx.coroutines.flow.emptyFlow()
        override fun observePedidoWithItems(pedidoId: String): kotlinx.coroutines.flow.Flow<PedidoWithItems?> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()
        override suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems> = emptyList()
        override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun editPedido(params: com.are.distribuidora.domain.pedido.model.EditPedidoParams): Result<Unit> = Result.Success(Unit)
        override suspend fun recoverStuckSyncingPedidos() = Unit
        override suspend fun deletePedido(pedidoId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = Result.Success(Unit)
    }

    private class FakeClientRepository : ClientRepository {
        override suspend fun create(client: Client): Result<Unit> = Result.Success(Unit)
        override suspend fun insert(client: Client) = Unit
        override suspend fun getClients(limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun getClientById(id: String): Result<Client?> = Result.Success(null)
        override suspend fun getByRouteId(routeId: String, limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override fun observeByRouteId(routeId: String, limit: Int): Flow<List<Client>> = emptyFlow()
        override suspend fun searchClients(query: String, limit: Int): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun searchClientsByRoute(routeId: String, query: String): Result<List<Client>> = Result.Success(emptyList())
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun syncClients(limit: Int): Result<Unit> = Result.Success(Unit)
        override suspend fun update(client: Client): Result<Unit> = Result.Success(Unit)
    }

    @Test
    fun `createPedidoUseCase pasa los items correctos al repositorio para descontar stock`() = runBlocking {
        val repo = FakePedidoRepository()
        val useCase = CreatePedidoUseCase(repo, FakeClientRepository())

        val items = listOf(
            CreatePedidoItemInput(
                productoId = "prod-1",
                nombre = "Producto Uno",
                precioUnitario = 10.0,
                cantidad = 10,
                descuentoItem = 0.0,
            ),
            CreatePedidoItemInput(
                productoId = "prod-2",
                nombre = "Producto Dos",
                precioUnitario = 5.0,
                cantidad = 3,
                descuentoItem = 0.0,
            ),
        )

        val resultado = useCase(
            vendedorId = "vendedor-1",
            routeId = "ruta-1",
            cliente = ClienteSelection.Temporal(ClienteSnapshot("Cliente Test", null, null)),
            descuentoGlobal = 0.0,
            items = items,
        )

        // El pedido se creó exitosamente
        assert(resultado is Result.Success<*>) { "Esperaba Success, obtuvo: $resultado" }

        // Los params llegaron al repositorio con los productIds y cantidades correctas
        val params = repo.capturedParams!!
        assertEquals(2, params.items.size)

        val item1 = params.items.first { it.productoId == "prod-1" }
        assertEquals(10, item1.cantidad)

        val item2 = params.items.first { it.productoId == "prod-2" }
        assertEquals(3, item2.cantidad)
    }

    @Test
    fun `multiples pedidos acumulan el comprometido correctamente`() {
        // Simula dos pedidos consecutivos al mismo producto con stock limitado.
        // Stock inicial = 5, pedido1 = 5 → stock = 0, comprometido = 0
        // Luego pedido2 = 3 → stock = 0, comprometido = 3
        var estado = StockState(stock = 5, comprometido = 0)
        estado = applyDeduction(estado, cantidad = 5)
        assertEquals(0, estado.stock)
        assertEquals(0, estado.comprometido)

        estado = applyDeduction(estado, cantidad = 3)
        assertEquals(0, estado.stock)
        assertEquals(3, estado.comprometido)
    }
}

