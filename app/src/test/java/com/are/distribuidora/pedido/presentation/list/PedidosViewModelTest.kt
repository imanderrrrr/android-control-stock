package com.are.distribuidora.pedido.presentation.list

import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoItem
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.SyncStatusLabel
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.usecase.DeletePedidoUseCase
import com.are.distribuidora.domain.pedido.usecase.ObserveAllPedidosUseCase
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import com.are.distribuidora.orders.domain.usecase.FetchAllOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.FetchOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteAndDateUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteUseCase
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.repository.RouteRepository
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PedidosViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakePedidoRepository(
        private val pedidos: List<PedidoWithItems> = emptyList(),
        private val throws: Exception? = null,
    ) : PedidoRepository {
        override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> {
            if (throws != null) throw throws
            return pedidos
        }

        override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> = flowOf(pedidos)
        override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> = flowOf(pedidos)
        override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> = kotlinx.coroutines.flow.emptyFlow()
        override suspend fun createPedido(params: CreatePedidoParams) = Result.Success("id")
        override suspend fun listPedidosByCliente(clienteId: String) = Result.Success(emptyList<Pedido>())
        override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = null
        override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = null
        override suspend fun getPendingPedidosForSync(limit: Int) = emptyList<PedidoWithItems>()
        override suspend fun getPendingUpdatePedidosForSync(limit: Int) = emptyList<PedidoWithItems>()
        override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun editPedido(params: com.are.distribuidora.domain.pedido.model.EditPedidoParams): Result<Unit> = Result.Success(Unit)
        override suspend fun recoverStuckSyncingPedidos() = Unit
        override suspend fun deletePedido(pedidoId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = Result.Success(Unit)
    }

    private class FakeRouteRepository(
        private val routes: List<Route> = emptyList(),
        private val throws: Exception? = null,
    ) : RouteRepository {
        override suspend fun getRoutes(limit: Int): Result<List<Route>> {
            if (throws != null) throw throws
            return Result.Success(routes)
        }
        override suspend fun create(route: Route) = Result.Success(Unit)
        override suspend fun getRouteById(id: String) = Result.Success(null)
        override suspend fun updateDeliveryDay(routeId: String, deliveryDay: Int) = Result.Success(Unit)
        override suspend fun assignClientToRoute(clientId: String, routeId: String) = Result.Success(Unit)
        override suspend fun existsById(routeId: String) = false
        override suspend fun countRoutes() = 0
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun getCurrentSession(): Session? = Session("uid", "test@test.com", "token", 0L)
        override suspend fun login(email: String, password: String): Result<Session> = Result.Success(Session("uid", "test@test.com", "token", 0L))
        override suspend fun logout(): Result<Unit> = Result.Success(Unit)
        override suspend fun isSessionActive(): Boolean = true
    }

    /** Fake mínimo de OrderRepository para inyectar en los nuevos use cases. */
    private class FakeOrderRepository(
        private val ordersByDate: Map<String, List<Order>> = emptyMap(),
    ) : OrderRepository {
        override suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String) = Result.Success(Unit)
        override suspend fun fetchAllOrdersHeader(routeId: String) = Result.Success(Unit)
        override suspend fun downloadOrderItems(orderId: String) = Result.Success(Unit)
        override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) = emptyList<Order>()
        override suspend fun deleteOrder(routeId: String, orderId: String) = Result.Success(Unit)
        override fun observeOrdersByRoute(routeId: String) = kotlinx.coroutines.flow.flowOf(emptyList<Order>())
        override fun observeOrdersByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<Order>> =
            kotlinx.coroutines.flow.flowOf(ordersByDate[deliveryDate] ?: emptyList())
        override suspend fun getOrderById(orderId: String): Order? = null
        override suspend fun getItemsByOrderId(orderId: String) = emptyList<com.are.distribuidora.orders.domain.model.OrderItem>()
    }

    private fun buildRoute(id: String, name: String) = Route(id, name, 1, 0, true, 0L, 0L)

    private fun buildOrder(
        orderId: String,
        routeId: String,
        deliveryDate: String,
    ) = Order(
        orderId         = orderId,
        routeId         = routeId,
        deliveryDate    = deliveryDate,
        clientName      = "Test Client",
        clientAddress   = null,
        sellerName      = "Other Seller",
        itemsCount      = 1,
        itemsDownloaded = 0,
        totalAmount     = null,
        downloadStatus  = com.are.distribuidora.orders.domain.model.OrderDownloadStatus.ITEMS_PENDING,
        failedReasonCode    = null,
        failedReasonMessage = null,
        failedAttempts      = 0,
        lastAttemptAt       = null,
        createdAt           = 0L,
        updatedAt           = 0L,
    )

    private fun buildItem(
        id: String = "item-1",
        nombre: String = "Agua 500ml",
        precio: Double = 5.0,
        cantidad: Int = 3,
        descuento: Double = 0.0,
        notes: String? = null,
    ) = PedidoItem(id, "prod-$id", nombre, precio, cantidad, descuento, (precio * cantidad) - descuento, notes)

    private fun buildPedidoWithItems(
        pedidoId: String = "pedido-1",
        routeId: String = "ruta-1",
        clienteNombre: String = "Juan Pérez",
        total: Double = 50.0,
        items: List<PedidoItem> = listOf(buildItem()),
        syncStatus: SyncStatusLabel = SyncStatusLabel.SYNCED,
    ) = PedidoWithItems(
        pedido = Pedido(
            id = pedidoId,
            vendedorId = "v1",
            routeId = routeId,
            deliveryDate = "2024-01-01",
            clienteId = "c1",
            clienteSnapshot = ClienteSnapshot(clienteNombre, null, null),
            items = items,
            subtotal = items.sumOf { it.totalItem },
            descuentoGlobal = 0.0,
            total = total,
            version = 1,
            actualizadoPor = "v1",
            creadoEn = 1_700_000_000_000L,
            actualizadoEn = 1_700_000_000_000L,
        ),
        items = items,
        syncStatusLabel = syncStatus,
    )

    private fun buildViewModel(
        pedidos: List<PedidoWithItems> = emptyList(),
        routes: List<Route> = emptyList(),
        pedidosThrows: Exception? = null,
        routesThrows: Exception? = null,
        orderRepository: FakeOrderRepository = FakeOrderRepository(),
    ): PedidosViewModel {
        val fakePedidoRepo = FakePedidoRepository(pedidos, pedidosThrows)
        val fakeRouteRepo  = FakeRouteRepository(routes, routesThrows)
        val fakeAuthRepo   = FakeAuthRepository()
        return PedidosViewModel(
            observeAllPedidosUseCase                   = ObserveAllPedidosUseCase(fakePedidoRepo),
            getRoutesUseCase                           = GetRoutesUseCase(fakeRouteRepo),
            deletePedidoUseCase                        = DeletePedidoUseCase(fakePedidoRepo),
            authRepository                             = fakeAuthRepo,
            observeOtherOrdersByRouteUseCase           = ObserveOtherOrdersByRouteUseCase(orderRepository),
            observeOtherOrdersByRouteAndDateUseCase    = ObserveOtherOrdersByRouteAndDateUseCase(orderRepository),
            downloadOrderItemsUseCase                  = DownloadOrderItemsUseCase(orderRepository),
            fetchOrdersHeaderUseCase                   = FetchOrdersHeaderUseCase(orderRepository),
            fetchAllOrdersHeaderUseCase                = FetchAllOrdersHeaderUseCase(orderRepository),
        )
    }

    @Test
    fun `estado inicial es Loading`() {
        val vm = buildViewModel()
        assertTrue(vm.uiState.value is PedidosViewModel.UiState.Loading)
    }

    @Test
    fun `sin pedidos el estado Success tiene lista vacia de rutas`() = runTest {
        val vm = buildViewModel(pedidos = emptyList())
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is PedidosViewModel.UiState.Success)
        assertTrue((state as PedidosViewModel.UiState.Success).routes.isEmpty())
    }

    @Test
    fun `con pedidos agrupa correctamente por ruta`() = runTest {
        val pedidos = listOf(
            buildPedidoWithItems(pedidoId = "p1", routeId = "ruta-A"),
            buildPedidoWithItems(pedidoId = "p2", routeId = "ruta-A"),
            buildPedidoWithItems(pedidoId = "p3", routeId = "ruta-B"),
        )
        val routes = listOf(
            buildRoute("ruta-A", "Ruta Norte"),
            buildRoute("ruta-B", "Ruta Sur"),
        )
        val vm = buildViewModel(pedidos = pedidos, routes = routes)
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as PedidosViewModel.UiState.Success
        assertEquals(2, state.routes.size)

        val rutaNorte = state.routes.first { it.routeId == "ruta-A" }
        assertEquals("Ruta Norte", rutaNorte.routeName)
        assertEquals(2, rutaNorte.pedidoCount)

        val rutaSur = state.routes.first { it.routeId == "ruta-B" }
        assertEquals("Ruta Sur", rutaSur.routeName)
        assertEquals(1, rutaSur.pedidoCount)
    }

    // ── Tests para los detalles (notes) de ítems ──────────────────────────────

    @Test
    fun `getItemsByPedido devuelve notes del item cuando existen`() = runTest {
        val itemConNotes = buildItem(id = "item-notas", notes = "Sin azúcar, extra limón")
        val pedido = buildPedidoWithItems(pedidoId = "p-notas", items = listOf(itemConNotes))
        val vm = buildViewModel(pedidos = listOf(pedido))
        dispatcher.scheduler.advanceUntilIdle()

        val uiItems = vm.getItemsByPedido("p-notas")
        assertEquals(1, uiItems.size)
        assertEquals("Sin azúcar, extra limón", uiItems[0].notes)
    }

    @Test
    fun `getItemsByPedido devuelve notes null cuando el item no tiene notas`() = runTest {
        val itemSinNotes = buildItem(id = "item-sin-notas")
        val pedido = buildPedidoWithItems(pedidoId = "p-sin-notas", items = listOf(itemSinNotes))
        val vm = buildViewModel(pedidos = listOf(pedido))
        dispatcher.scheduler.advanceUntilIdle()

        val uiItems = vm.getItemsByPedido("p-sin-notas")
        assertEquals(1, uiItems.size)
        assertEquals(null, uiItems[0].notes)
    }

    @Test
    fun `getItemsByPedido filtra notes en blanco como null`() = runTest {
        val itemNotesBlank = buildItem(id = "item-blank", notes = "   ")
        val pedido = buildPedidoWithItems(pedidoId = "p-blank", items = listOf(itemNotesBlank))
        val vm = buildViewModel(pedidos = listOf(pedido))
        dispatcher.scheduler.advanceUntilIdle()

        val uiItems = vm.getItemsByPedido("p-blank")
        assertEquals(1, uiItems.size)
        // Notes en blanco debe ser null en la UI
        assertEquals(null, uiItems[0].notes)
    }

    @Test
    fun `getItemsByPedido mapea correctamente multiples items con y sin notes`() = runTest {
        val item1 = buildItem(id = "item-a", nombre = "Agua", notes = "Fría")
        val item2 = buildItem(id = "item-b", nombre = "Pan")
        val item3 = buildItem(id = "item-c", nombre = "Leche", notes = "Descremada")
        val pedido = buildPedidoWithItems(
            pedidoId = "p-multi",
            items = listOf(item1, item2, item3),
        )
        val vm = buildViewModel(pedidos = listOf(pedido))
        dispatcher.scheduler.advanceUntilIdle()

        val uiItems = vm.getItemsByPedido("p-multi")
        assertEquals(3, uiItems.size)

        val uiA = uiItems.first { it.nombre == "Agua" }
        val uiB = uiItems.first { it.nombre == "Pan" }
        val uiC = uiItems.first { it.nombre == "Leche" }

        assertEquals("Fría", uiA.notes)
        assertEquals(null, uiB.notes)
        assertEquals("Descremada", uiC.notes)
    }

    // ── Tests de otrosSelectedDate (estado independiente de Mis Pedidos) ──────

    @Test
    fun `otrosSelectedDate por defecto es hoy`() {
        val vm = buildViewModel()
        val today = vm.todayAsDbString()
        assertEquals("El valor por defecto debe ser hoy", today, vm.otrosSelectedDate.value)
    }

    @Test
    fun `setOtrosSelectedDate cambia el valor de otrosSelectedDate`() = runTest {
        val vm = buildViewModel()
        val newDate = "2026-02-25"
        vm.setOtrosSelectedDate(newDate)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(newDate, vm.otrosSelectedDate.value)
    }

    @Test
    fun `setOtrosSelectedDate no modifica selectedDate de Mis Pedidos`() = runTest {
        val vm = buildViewModel()
        val misPedidosDate = vm.selectedDate.value
        vm.setOtrosSelectedDate("2026-01-01")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "selectedDate de Mis Pedidos no debe cambiar al cambiar otrosSelectedDate",
            misPedidosDate,
            vm.selectedDate.value,
        )
    }

    @Test
    fun `setSelectedDate no modifica otrosSelectedDate`() = runTest {
        val vm = buildViewModel()
        val otrosDate = vm.otrosSelectedDate.value
        vm.setSelectedDate("2026-01-15")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            "otrosSelectedDate no debe cambiar al cambiar selectedDate de Mis Pedidos",
            otrosDate,
            vm.otrosSelectedDate.value,
        )
    }

    @Test
    fun `ObserveOtherOrdersByRouteAndDateUseCase devuelve dataset correcto por fecha`() = runTest {
        val orderDateA = buildOrder("ORDER-A", "ROUTE-1", "2026-02-24")
        val orderDateB = buildOrder("ORDER-B", "ROUTE-1", "2026-02-25")

        val fakeOrderRepo = FakeOrderRepository(
            ordersByDate = mapOf(
                "2026-02-24" to listOf(orderDateA),
                "2026-02-25" to listOf(orderDateB),
            )
        )
        val useCase = ObserveOtherOrdersByRouteAndDateUseCase(fakeOrderRepo)

        val resultA = useCase("ROUTE-1", "2026-02-24").first()
        assertEquals("Debe haber 1 order para 2026-02-24", 1, resultA.size)
        assertEquals("ORDER-A", resultA[0].orderId)

        val resultB = useCase("ROUTE-1", "2026-02-25").first()
        assertEquals("Debe haber 1 order para 2026-02-25", 1, resultB.size)
        assertEquals("ORDER-B", resultB[0].orderId)
    }
}
