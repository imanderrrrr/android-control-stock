package com.are.distribuidora.orders

import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderDownloadStatus
import com.are.distribuidora.orders.domain.model.OrderItem
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.orders.domain.usecase.GetOtrosPedidoDetalleUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para [GetOtrosPedidoDetalleUseCase].
 *
 * Verifica:
 * 1. Pedido existente con ítems → devuelve Order + lista de ítems.
 * 2. Pedido inexistente (null) → devuelve order=null, items=emptyList.
 * 3. Pedido existente sin ítems → devuelve Order + items vacíos.
 * 4. orderId vacío → devuelve order=null, items=emptyList (guard del repositorio).
 */
class GetOtrosPedidoDetalleUseCaseTest {

    // ── Fake repository ───────────────────────────────────────────────────────

    private fun buildFakeRepo(
        orderById: Map<String, Order> = emptyMap(),
        itemsByOrderId: Map<String, List<OrderItem>> = emptyMap(),
    ): OrderRepository = object : OrderRepository {

        override suspend fun getOrderById(orderId: String): Order? =
            orderById[orderId]

        override suspend fun getItemsByOrderId(orderId: String): List<OrderItem> =
            itemsByOrderId[orderId] ?: emptyList()

        // Métodos no usados por este UseCase — implementación vacía/stub
        override suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String) =
            com.are.distribuidora.core.result.Result.Success(Unit)

        override suspend fun fetchAllOrdersHeader(routeId: String) =
            com.are.distribuidora.core.result.Result.Success(Unit)

        override suspend fun downloadOrderItems(orderId: String) =
            com.are.distribuidora.core.result.Result.Success(Unit)

        override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
            emptyList<Order>()

        override suspend fun deleteOrder(routeId: String, orderId: String) =
            com.are.distribuidora.core.result.Result.Success(Unit)

        override fun observeOrdersByRoute(routeId: String): Flow<List<Order>> =
            flowOf(emptyList())

        override fun observeOrdersByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<Order>> =
            flowOf(emptyList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeOrder(orderId: String) = Order(
        orderId          = orderId,
        routeId          = "ROUTE-1",
        deliveryDate     = "2026-02-22",
        clientName       = "Cliente Test",
        clientAddress    = null,
        sellerName       = "Vendedor Otro",
        itemsCount       = 2,
        itemsDownloaded  = 2,
        totalAmount      = 50.0,
        downloadStatus   = OrderDownloadStatus.COMPLETED,
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts   = 0,
        lastAttemptAt    = null,
        createdAt        = 1_000_000L,
        updatedAt        = 1_000_001L,
        isDeleted        = false,
    )

    private fun makeItem(orderId: String, productId: String) = OrderItem(
        orderId     = orderId,
        productId   = productId,
        productName = "Producto $productId",
        unitPrice   = 25.0,
        quantity    = 1,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `invoke - pedido existente con items devuelve Order y lista correcta`() = runBlocking {
        val orderId = "ORDER-COMPLETO"
        val order   = makeOrder(orderId)
        val items   = listOf(makeItem(orderId, "P1"), makeItem(orderId, "P2"))

        val repo    = buildFakeRepo(
            orderById      = mapOf(orderId to order),
            itemsByOrderId = mapOf(orderId to items),
        )
        val useCase = GetOtrosPedidoDetalleUseCase(repo)
        val result  = useCase(orderId)

        assertNotNull("Order no debe ser null", result.order)
        assertEquals("clientName debe coincidir", "Cliente Test", result.order?.clientName)
        assertEquals("Debe devolver 2 ítems", 2, result.items.size)
        assertEquals("lineTotal correcto", 25.0, result.items[0].lineTotal, 0.001)
    }

    @Test
    fun `invoke - pedido inexistente devuelve order null y lista vacía`() = runBlocking {
        val repo    = buildFakeRepo() // sin datos
        val useCase = GetOtrosPedidoDetalleUseCase(repo)
        val result  = useCase("ORDER-NO-EXISTE")

        assertNull("Order debe ser null", result.order)
        assertTrue("Items debe estar vacío", result.items.isEmpty())
    }

    @Test
    fun `invoke - pedido existente sin items descargados devuelve Order con lista vacía`() = runBlocking {
        val orderId = "ORDER-SIN-ITEMS"
        val order   = makeOrder(orderId)

        val repo    = buildFakeRepo(
            orderById      = mapOf(orderId to order),
            itemsByOrderId = emptyMap(), // sin ítems
        )
        val useCase = GetOtrosPedidoDetalleUseCase(repo)
        val result  = useCase(orderId)

        assertNotNull("Order no debe ser null", result.order)
        assertTrue("Items debe estar vacío", result.items.isEmpty())
    }

    @Test
    fun `invoke - orderId vacío devuelve order null y lista vacía`() = runBlocking {
        val repo    = buildFakeRepo()
        val useCase = GetOtrosPedidoDetalleUseCase(repo)
        val result  = useCase("")

        assertNull("Order debe ser null para orderId vacío", result.order)
        assertTrue("Items debe estar vacío para orderId vacío", result.items.isEmpty())
    }
}

