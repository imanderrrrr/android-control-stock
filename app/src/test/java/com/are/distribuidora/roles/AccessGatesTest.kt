package com.are.distribuidora.roles

import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.usecase.ValidateOrderLimitUseCase
import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.SyncStatusLabel
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoItemInput
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.ReportParams
import com.are.distribuidora.domain.pedido.model.ReportResult
import com.are.distribuidora.domain.pedido.usecase.DeletePedidoUseCase
import com.are.distribuidora.domain.pedido.usecase.EditPedidoUseCase
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderDownloadStatus
import com.are.distribuidora.orders.domain.model.OrderItem
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.orders.domain.usecase.DeleteOrderUseCase
import com.are.distribuidora.orders.domain.usecase.EditOtrosOrderUseCase
import com.are.distribuidora.screenaccess.domain.model.UserAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un test por caso de uso gateado: con rol vendedor y pedido ajeno ⇒ Failure.Forbidden;
 * con pedido propio o rol admin ⇒ llega al repositorio.
 */
class AccessGatesTest {

    private val me = "uid-me"
    private val other = "uid-other"

    // ── Fakes pedidos/ ─────────────────────────────────────────────────────

    private class FakePedidoRepository(private val owner: String?) : PedidoRepository {
        var editCalls = 0
        var deleteCalls = 0
        private fun pedido(): PedidoWithItems? = owner?.let {
            PedidoWithItems(
                pedido = Pedido(
                    id = "p1", vendedorId = it, routeId = "r1", clienteId = "c1",
                    clienteSnapshot = ClienteSnapshot("Cli", null, null), items = emptyList(),
                    subtotal = 0.0, descuentoGlobal = 0.0, total = 0.0, version = 1,
                    actualizadoPor = it, creadoEn = 0L, actualizadoEn = 0L,
                ),
                items = emptyList(), syncStatusLabel = SyncStatusLabel.SYNCED,
            )
        }
        override suspend fun getAllPedidosWithItems() = listOfNotNull(pedido())
        override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> = flowOf(listOfNotNull(pedido()))
        override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> = flowOf(listOfNotNull(pedido()))
        override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> = flowOf(pedido())
        override suspend fun createPedido(params: CreatePedidoParams) = Result.Success("id")
        override suspend fun listPedidosByCliente(clienteId: String) = Result.Success(emptyList<Pedido>())
        override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = null
        override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = null
        override suspend fun getPendingPedidosForSync(limit: Int) = emptyList<PedidoWithItems>()
        override suspend fun getPendingUpdatePedidosForSync(limit: Int) = emptyList<PedidoWithItems>()
        override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) = Unit
        override suspend fun editPedido(params: EditPedidoParams): Result<Unit> { editCalls++; return Result.Success(Unit) }
        override suspend fun recoverStuckSyncingPedidos() = Unit
        override suspend fun deletePedido(pedidoId: String): Result<Unit> { deleteCalls++; return Result.Success(Unit) }
        override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun getReportData(params: ReportParams): ReportResult =
            ReportResult(0.0, 0, 0.0, 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }

    private class FakeClientRepository : ClientRepository {
        override suspend fun create(client: Client) = Result.Success(Unit)
        override suspend fun insert(client: Client) = Unit
        override suspend fun getClients(limit: Int) = Result.Success(emptyList<Client>())
        override suspend fun getClientById(id: String): Result<Client?> = Result.Success(null)
        override suspend fun getByRouteId(routeId: String, limit: Int) = Result.Success(emptyList<Client>())
        override fun observeByRouteId(routeId: String, limit: Int): Flow<List<Client>> = emptyFlow()
        override suspend fun searchClients(query: String, limit: Int) = Result.Success(emptyList<Client>())
        override suspend fun searchClientsByRoute(routeId: String, query: String) = Result.Success(emptyList<Client>())
        override suspend fun delete(id: String) = Result.Success(Unit)
        override suspend fun syncClients(limit: Int) = Result.Success(Unit)
        override suspend fun update(client: Client) = Result.Success(Unit)
    }

    private inner class FakeAuthRepository : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Session> = Result.Success(session())
        override suspend fun logout(): Result<Unit> = Result.Success(Unit)
        override suspend fun getCurrentSession(): Session? = session()
        override suspend fun isSessionActive(): Boolean = true
        private fun session() = Session(me, "me@test.com", "tok", 0L)
    }

    private fun editParams(vendedorId: String) = EditPedidoParams(
        pedidoId = "p1", vendedorId = vendedorId, clienteId = null,
        itemsToUpsert = listOf(EditPedidoItemInput("i1", "prod", "Prod", 10.0, 1, 0.0)),
        itemIdsToDelete = emptyList(), previousItems = emptyList(), descuentoGlobal = 0.0,
    )

    private fun vendedor() = UserAccess.leastPrivilege()
    private fun admin() = UserAccess.admin()

    // ── EditPedidoUseCase ──────────────────────────────────────────────────

    @Test fun `EditPedido - vendedor no edita pedido ajeno`() = runTest {
        val repo = FakePedidoRepository(owner = other)
        val uc = EditPedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase()) { vendedor() }
        val r = uc(editParams(me))
        assertTrue(r is Result.Error && r.failure == Failure.Forbidden)
        assertEquals(0, repo.editCalls)
    }

    @Test fun `EditPedido - vendedor edita pedido propio`() = runTest {
        val repo = FakePedidoRepository(owner = me)
        val uc = EditPedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase()) { vendedor() }
        assertTrue(uc(editParams(me)) is Result.Success)
        assertEquals(1, repo.editCalls)
    }

    @Test fun `EditPedido - admin edita pedido ajeno`() = runTest {
        val repo = FakePedidoRepository(owner = other)
        val uc = EditPedidoUseCase(repo, FakeClientRepository(), ValidateOrderLimitUseCase()) { admin() }
        assertTrue(uc(editParams(me)) is Result.Success)
    }

    // ── DeletePedidoUseCase ────────────────────────────────────────────────

    @Test fun `DeletePedido - vendedor no borra pedido ajeno`() = runTest {
        val repo = FakePedidoRepository(owner = other)
        val uc = DeletePedidoUseCase(repo, { vendedor() }, FakeAuthRepository())
        val r = uc("p1")
        assertTrue(r is Result.Error && r.failure == Failure.Forbidden)
        assertEquals(0, repo.deleteCalls)
    }

    @Test fun `DeletePedido - vendedor borra pedido propio`() = runTest {
        val repo = FakePedidoRepository(owner = me)
        val uc = DeletePedidoUseCase(repo, { vendedor() }, FakeAuthRepository())
        assertTrue(uc("p1") is Result.Success)
        assertEquals(1, repo.deleteCalls)
    }

    @Test fun `DeletePedido - pedido sin dueño conocido se rechaza a vendedor`() = runTest {
        val repo = FakePedidoRepository(owner = null)
        val uc = DeletePedidoUseCase(repo, { vendedor() }, FakeAuthRepository())
        assertTrue((uc("p1") as Result.Error).failure == Failure.Forbidden)
    }

    // ── Fakes orders/ ──────────────────────────────────────────────────────

    private class FakeOrderRepository(private val owner: String?) : OrderRepository {
        var editCalls = 0
        var deleteCalls = 0
        private fun order() = Order(
            orderId = "o1", routeId = "r1", deliveryDate = "2026-09-08", clientName = "Cli",
            clientAddress = null, sellerName = "S", itemsCount = 1, itemsDownloaded = 1, totalAmount = 10.0,
            downloadStatus = OrderDownloadStatus.COMPLETED, failedReasonCode = null, failedReasonMessage = null,
            failedAttempts = 0, lastAttemptAt = null, createdAt = 0L, updatedAt = 0L, vendedorId = owner,
        )
        override suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String) = Result.Success(Unit)
        override suspend fun fetchAllOrdersHeader(routeId: String) = Result.Success(Unit)
        override suspend fun downloadOrderItems(orderId: String) = Result.Success(Unit)
        override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) = listOf(order())
        override suspend fun deleteOrder(routeId: String, orderId: String): Result<Unit> { deleteCalls++; return Result.Success(Unit) }
        override fun observeOrdersByRoute(routeId: String) = flowOf(listOf(order()))
        override fun observeOrdersByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<Order>> = flowOf(listOf(order()))
        override suspend fun getOrderById(orderId: String): Order? = order()
        override suspend fun getItemsByOrderId(orderId: String) = emptyList<OrderItem>()
        override suspend fun editOrderItems(orderId: String, items: List<EditOrderItemInput>): Result<Unit> { editCalls++; return Result.Success(Unit) }
        override suspend fun uploadPendingOrders() = Result.Success(Unit)
    }

    private fun uid(v: String?) = object : CurrentUserIdProvider { override fun get() = v }
    private val items = listOf(EditOrderItemInput("i1", "prod", "Prod", 10.0, 1, 0.0, null))

    // ── EditOtrosOrderUseCase ──────────────────────────────────────────────

    @Test fun `EditOtrosOrder - vendedor no edita pedido ajeno`() = runTest {
        val repo = FakeOrderRepository(owner = other)
        val r = EditOtrosOrderUseCase(repo, { vendedor() }, uid(me))("o1", items)
        assertTrue(r is Result.Error && r.failure == Failure.Forbidden)
        assertEquals(0, repo.editCalls)
    }

    @Test fun `EditOtrosOrder - vendedor edita el suyo y admin cualquiera`() = runTest {
        val mine = FakeOrderRepository(owner = me)
        assertTrue(EditOtrosOrderUseCase(mine, { vendedor() }, uid(me))("o1", items) is Result.Success)
        val theirs = FakeOrderRepository(owner = other)
        assertTrue(EditOtrosOrderUseCase(theirs, { admin() }, uid(me))("o1", items) is Result.Success)
        assertEquals(1, theirs.editCalls)
    }

    // ── DeleteOrderUseCase ─────────────────────────────────────────────────

    @Test fun `DeleteOrder - vendedor no borra pedido ajeno`() = runTest {
        val repo = FakeOrderRepository(owner = other)
        val r = DeleteOrderUseCase(repo, { vendedor() }, uid(me)).execute("r1", "o1")
        assertTrue(r is Result.Error && r.failure == Failure.Forbidden)
        assertEquals(0, repo.deleteCalls)
    }

    @Test fun `DeleteOrder - admin borra pedido ajeno`() = runTest {
        val repo = FakeOrderRepository(owner = other)
        assertTrue(DeleteOrderUseCase(repo, { admin() }, uid(me)).execute("r1", "o1") is Result.Success)
        assertEquals(1, repo.deleteCalls)
    }

    @Test fun `DeleteOrder - sin uid de Firebase el vendedor no borra ni el suyo`() = runTest {
        val repo = FakeOrderRepository(owner = me)
        val r = DeleteOrderUseCase(repo, { vendedor() }, uid(null)).execute("r1", "o1")
        assertFalse(r is Result.Success)
    }
}
