package com.are.distribuidora.orders

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.orders.domain.model.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para la descarga de pedidos ÚNICAMENTE con headers (sin items).
 *
 * Verifica el comportamiento de [OfflineFirstOrderRepository.fetchOrdersHeader]:
 * 1. Headers de otros vendedores se persisten correctamente
 * 2. Headers propios son excluidos (Opción B)
 * 3. Headers eliminados remotamente (isDeleted=true) se purgan localmente
 * 4. Headers con itemsCount <= 0 son ignorados
 * 5. Headers incompletos (campos vacíos) son ignorados
 * 6. El estado queda en ITEMS_PENDING (items NO descargados)
 * 7. [observeOrdersByRoute] filtra pedidos propios en el Flow reactivo
 */
class OtrosOrdersHeaderOnlyTest {

    private val routeId      = "ROUTE-001"
    private val deliveryDate = "2026-02-22"
    private val myUid        = "uid-vendedor-propio"
    private val otherUid     = "uid-vendedor-otro"

    private val persisted = mutableMapOf<String, OrderEntity>()
    private val purgeCalls = mutableListOf<String>() // orderId purgados por markOrderDeleted

    @Before
    fun setUp() {
        persisted.clear()
        purgeCalls.clear()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildRepository(
        uid: String?,
        remoteHeaders: List<OrderRemoteDataSource.OrderHeaderDto>,
    ): OfflineFirstOrderRepository {
        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) {
                persisted[entity.orderId] = entity
            }
            override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {
                persisted.entries
                    .filter { it.value.routeId == routeId && it.value.deliveryDate == deliveryDate && it.value.vendedorId == vendedorId }
                    .forEach { persisted[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }
            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
                persisted.entries
                    .filter { it.value.routeId == routeId && it.value.vendedorId == vendedorId }
                    .forEach { persisted[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }
            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                purgeCalls.add(orderId)
                persisted[orderId]?.let { persisted[orderId] = it.copy(isDeleted = true, updatedAt = now) }
            }
            override suspend fun deleteItemsByOrderId(orderId: String) {}
            override suspend fun getOrderById(orderId: String): OrderEntity? = persisted[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                persisted.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted }
            override suspend fun markInProgress(orderId: String, now: Long) {}
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {}
            override suspend fun clearStaging(orderId: String) {}
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {}
            override suspend fun countStaging(orderId: String): Int = 0
            override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> = emptyList()
            override suspend fun commitItems(orderId: String, finalItems: List<OrderItemEntity>, totalAmount: Double, itemsDownloaded: Int, now: Long) {}
            override fun observeByRoute(routeId: String): Flow<List<OrderEntity>> =
                flowOf(persisted.values.filter { it.routeId == routeId && !it.isDeleted })

            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<OrderEntity>> =
                flowOf(persisted.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })

            override suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity> = emptyList()
        }

        val fakeRemote = object : OrderRemoteDataSource {
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = remoteHeaders
            override suspend fun fetchAllOrderHeaders(routeId: String) = remoteHeaders
            override suspend fun fetchOrderItems(routeId: String, orderId: String) = emptyList<OrderRemoteDataSource.OrderItemDto>()
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {}
        }

        return OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get() = uid },
        )
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `fetchOrdersHeader - headers de otro vendedor se persisten con status ITEMS_PENDING`() = runBlocking {
        val headers = listOf(
            makeHeader("ORDER-1", vendedorId = otherUid),
            makeHeader("ORDER-2", vendedorId = otherUid),
        )
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        val result = repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertTrue("fetchOrdersHeader debe ser exitoso", result.isSuccess())
        assertEquals("Deben persistirse 2 headers", 2, persisted.size)
        persisted.values.forEach { entity ->
            assertEquals("Status debe ser ITEMS_PENDING", "ITEMS_PENDING", entity.downloadStatus)
            assertEquals(0, entity.itemsDownloaded)
        }
    }

    @Test
    fun `fetchOrdersHeader - header propio es excluido y no se persiste`() = runBlocking {
        val headers = listOf(
            makeHeader("ORDER-PROPIO", vendedorId = myUid),   // propio → excluir
            makeHeader("ORDER-AJENO", vendedorId = otherUid), // otro → incluir
        )
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertFalse("Header propio NO debe persistirse", persisted.containsKey("ORDER-PROPIO"))
        assertTrue("Header ajeno SÍ debe persistirse", persisted.containsKey("ORDER-AJENO"))
    }

    @Test
    fun `fetchOrdersHeader - header con isDeleted=true se purga localmente`() = runBlocking {
        // Pre-persistir un order que luego llega como eliminado
        persisted["ORDER-DELETED"] = makeEntity("ORDER-DELETED", downloadStatus = "ITEMS_PENDING")

        val headers = listOf(makeHeader("ORDER-DELETED", vendedorId = otherUid, isDeleted = true))
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertTrue("Header eliminado debe marcarse como purgado", purgeCalls.contains("ORDER-DELETED"))
        assertFalse("Header eliminado NO debe existir como activo", persisted["ORDER-DELETED"]?.isDeleted == false)
    }

    @Test
    fun `fetchOrdersHeader - header con itemsCount 0 es ignorado`() = runBlocking {
        val headers = listOf(makeHeader("ORDER-ZERO-ITEMS", vendedorId = otherUid, itemsCount = 0))
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertFalse("Header con itemsCount=0 NO debe persistirse", persisted.containsKey("ORDER-ZERO-ITEMS"))
    }

    @Test
    fun `fetchOrdersHeader - header incompleto (clientName vacío) es ignorado`() = runBlocking {
        val headers = listOf(
            OrderRemoteDataSource.OrderHeaderDto(
                orderId = "ORDER-INCOMPLETE",
                routeId = routeId,
                deliveryDate = deliveryDate,
                clientName = "",   // campo obligatorio vacío
                clientAddress = null,
                sellerName = null,
                itemsCount = 3,
                vendedorId = otherUid,
                isDeleted = false,
            )
        )
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertFalse("Header incompleto NO debe persistirse", persisted.containsKey("ORDER-INCOMPLETE"))
    }

    @Test
    fun `fetchOrdersHeader - uid null conserva todos los headers sin filtro`() = runBlocking {
        val headers = listOf(
            makeHeader("ORDER-A", vendedorId = otherUid),
            makeHeader("ORDER-B", vendedorId = myUid),  // con uid=null no se filtra
        )
        val repo = buildRepository(uid = null, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertEquals("Sin uid deben persistirse todos los headers", 2, persisted.size)
    }

    @Test
    fun `fetchOrdersHeader - header existente COMPLETED solo actualiza metadatos`() = runBlocking {
        // Pre-persistir order ya completado
        val existing = makeEntity(
            orderId = "ORDER-COMPLETED",
            downloadStatus = "COMPLETED",
            itemsDownloaded = 3,
            totalAmount = 150.0,
        )
        persisted["ORDER-COMPLETED"] = existing

        val headers = listOf(makeHeader("ORDER-COMPLETED", vendedorId = otherUid, itemsCount = 4))
        val repo = buildRepository(uid = myUid, remoteHeaders = headers)

        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        val updated = persisted["ORDER-COMPLETED"]!!
        assertEquals("Status debe seguir COMPLETED", "COMPLETED", updated.downloadStatus)
        assertEquals("Items descargados no deben resetearse", 3, updated.itemsDownloaded)
        assertEquals("Total no debe resetearse", 150.0, updated.totalAmount)
        assertEquals("itemsCount debe actualizarse al nuevo valor", 4, updated.itemsCount)
    }

    @Test
    fun `fetchOrdersHeader - lista vacía desde remoto retorna éxito sin persistir nada`() = runBlocking {
        val repo = buildRepository(uid = myUid, remoteHeaders = emptyList())

        val result = repo.fetchOrdersHeader(routeId = routeId, deliveryDate = deliveryDate)

        assertTrue(result.isSuccess())
        assertTrue("No debe haberse persistido nada", persisted.isEmpty())
    }

    @Test
    fun `observeOrdersByRoute - filtra pedidos propios en el Flow`() = runBlocking {
        // Pre-poblar con pedidos propios y ajenos
        persisted["ORDER-PROPIO"] = makeEntity("ORDER-PROPIO", vendedorId = myUid)
        persisted["ORDER-AJENO"]  = makeEntity("ORDER-AJENO",  vendedorId = otherUid)

        val repo = buildRepository(uid = myUid, remoteHeaders = emptyList())

        val orders = mutableListOf<Order>()
        // Consumir primera emisión del flow
        repo.observeOrdersByRoute(routeId).collect { list ->
            orders.addAll(list)
            return@collect
        }

        assertFalse("Pedido propio NO debe aparecer en el Flow", orders.any { it.orderId == "ORDER-PROPIO" })
        assertTrue("Pedido ajeno SÍ debe aparecer en el Flow", orders.any { it.orderId == "ORDER-AJENO" })
    }

    @Test
    fun `observeOrdersByRoute - uid null no filtra ningún pedido en el Flow`() = runBlocking {
        persisted["ORDER-A"] = makeEntity("ORDER-A", vendedorId = myUid)
        persisted["ORDER-B"] = makeEntity("ORDER-B", vendedorId = otherUid)

        val repo = buildRepository(uid = null, remoteHeaders = emptyList())

        val orders = mutableListOf<Order>()
        repo.observeOrdersByRoute(routeId).collect { list ->
            orders.addAll(list)
            return@collect
        }

        assertEquals("Sin uid deben mostrarse todos los pedidos", 2, orders.size)
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private fun makeHeader(
        orderId: String,
        vendedorId: String? = null,
        itemsCount: Int = 3,
        isDeleted: Boolean = false,
    ) = OrderRemoteDataSource.OrderHeaderDto(
        orderId      = orderId,
        routeId      = routeId,
        deliveryDate = deliveryDate,
        clientName   = "Cliente $orderId",
        clientAddress = null,
        sellerName   = null,
        itemsCount   = itemsCount,
        vendedorId   = vendedorId,
        isDeleted    = isDeleted,
    )

    private fun makeEntity(
        orderId: String,
        vendedorId: String? = null,
        downloadStatus: String = "ITEMS_PENDING",
        itemsDownloaded: Int = 0,
        totalAmount: Double? = null,
    ) = OrderEntity(
        orderId         = orderId,
        routeId         = routeId,
        deliveryDate    = deliveryDate,
        clientName      = "Cliente $orderId",
        clientAddress   = null,
        sellerName      = null,
        itemsCount      = 3,
        itemsDownloaded = itemsDownloaded,
        totalAmount     = totalAmount,
        downloadStatus  = downloadStatus,
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts  = 0,
        lastAttemptAt   = null,
        createdAt       = 1_000_000L,
        updatedAt       = 1_000_000L,
        vendedorId      = vendedorId,
        isDeleted       = false,
    )
}

