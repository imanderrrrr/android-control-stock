package com.are.distribuidora.orders

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para la descarga de ITEMS de pedidos de otros vendedores.
 *
 * Verifica [OfflineFirstOrderRepository.downloadOrderItems] cuando el header
 * ya está persistido (ITEMS_PENDING) y el usuario pulsa el botón de descarga:
 *
 * 1. Descarga exitosa → status COMPLETED, items en finalItems
 * 2. Header no encontrado → Result.Error(NotFound)
 * 3. Header eliminado → Result.Error(ValidationError "ORDER_DELETED")
 * 4. Header de pedido propio → Result.Error(ValidationError "OWN_ORDER_EXCLUDED")
 * 5. Header ya COMPLETED → idempotencia (no vuelve a descargar)
 * 6. Remoto vacío → status FAILED, sin commit
 * 7. Fallo de red → Result.Error(NetworkError)
 * 8. COUNT_MISMATCH (staging ≠ normalizedItems) → FAILED
 */
class OtrosOrdersDownloadItemsTest {

    private val routeId  = "ROUTE-001"
    private val myUid    = "uid-propio"
    private val otherUid = "uid-otro"

    private val localStore      = mutableMapOf<String, OrderEntity>()
    private val stagingStore    = mutableMapOf<String, MutableList<OrderItemStagingEntity>>()
    private val finalItemsStore = mutableMapOf<String, MutableList<OrderItemEntity>>()
    private val commitCalled    = mutableListOf<String>()
    private val failedStore     = mutableMapOf<String, String>() // orderId → reasonCode

    private var remoteItemsToReturn: List<OrderRemoteDataSource.OrderItemDto> = emptyList()
    private var remoteThrows: Exception? = null

    @Before
    fun setUp() {
        localStore.clear()
        stagingStore.clear()
        finalItemsStore.clear()
        commitCalled.clear()
        failedStore.clear()
        remoteItemsToReturn = emptyList()
        remoteThrows = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildRepository(uid: String?): OfflineFirstOrderRepository {
        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) {
                localStore[entity.orderId] = entity
            }
            override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {}
            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {}
            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                localStore[orderId]?.let { localStore[orderId] = it.copy(isDeleted = true) }
            }
            override suspend fun deleteItemsByOrderId(orderId: String) {
                finalItemsStore.remove(orderId)
                stagingStore.remove(orderId)
            }
            override suspend fun getOrderById(orderId: String) = localStore[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                localStore.values.filter { it.routeId == routeId && !it.isDeleted }
            override suspend fun markInProgress(orderId: String, now: Long) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(downloadStatus = "IN_PROGRESS", lastAttemptAt = now)
            }
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(
                    downloadStatus  = "FAILED",
                    failedReasonCode = reasonCode,
                    failedAttempts  = c.failedAttempts + 1,
                )
                failedStore[orderId] = reasonCode
            }
            override suspend fun clearStaging(orderId: String) {
                stagingStore.remove(orderId)
            }
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
                items.forEach { item ->
                    stagingStore.getOrPut(item.orderId) { mutableListOf() }.add(item)
                }
            }
            override suspend fun countStaging(orderId: String) = stagingStore[orderId]?.size ?: 0
            override suspend fun getStaging(orderId: String) = stagingStore[orderId] ?: emptyList()
            override suspend fun commitItems(
                orderId: String,
                finalItems: List<OrderItemEntity>,
                totalAmount: Double,
                itemsDownloaded: Int,
                now: Long,
            ) {
                commitCalled.add(orderId)
                finalItemsStore[orderId] = finalItems.toMutableList()
                stagingStore.remove(orderId)
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(
                    downloadStatus  = "COMPLETED",
                    itemsDownloaded = itemsDownloaded,
                    totalAmount     = totalAmount,
                )
            }
            override fun observeByRoute(routeId: String): Flow<List<OrderEntity>> =
                flowOf(localStore.values.filter { it.routeId == routeId && !it.isDeleted })

            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<OrderEntity>> =
                flowOf(localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })

            override suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity> =
                finalItemsStore[orderId] ?: emptyList()
        }

        val fakeRemote = object : OrderRemoteDataSource {
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
            override suspend fun fetchAllOrderHeaders(routeId: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
            override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
                remoteThrows?.let { throw it }
                return remoteItemsToReturn
            }
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {}
        }

        return OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get() = uid },
        )
    }

    private fun makeHeader(
        orderId: String,
        vendedorId: String?,
        downloadStatus: String = "ITEMS_PENDING",
        itemsCount: Int = 2,
        isDeleted: Boolean = false,
        itemsDownloaded: Int = 0,
        totalAmount: Double? = null,
    ) = OrderEntity(
        orderId         = orderId,
        routeId         = routeId,
        deliveryDate    = "2026-02-22",
        clientName      = "Cliente Test",
        clientAddress   = null,
        sellerName      = null,
        itemsCount      = itemsCount,
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
        isDeleted       = isDeleted,
    )

    private fun makeRemoteItem(productId: String, qty: Int = 1) =
        OrderRemoteDataSource.OrderItemDto(
            itemId      = "item-$productId",
            productId   = productId,
            productName = "Producto $productId",
            unitPrice   = 10.0,
            quantity    = qty,
        )

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `downloadOrderItems - descarga exitosa resulta en COMPLETED con items almacenados`() = runBlocking {
        val orderId = "ORDER-OK"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, itemsCount = 2)
        remoteItemsToReturn = listOf(makeRemoteItem("P1"), makeRemoteItem("P2"))

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue("Debe ser Success", result.isSuccess())
        assertEquals("Status debe ser COMPLETED", "COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals("Debe haber 2 items en finalItems", 2, finalItemsStore[orderId]?.size)
        assertTrue("commitItems debe haberse llamado", commitCalled.contains(orderId))
    }

    @Test
    fun `downloadOrderItems - header no encontrado retorna NotFound`() = runBlocking {
        val repo = buildRepository(uid = myUid)

        val result = repo.downloadOrderItems("ORDER-NO-EXISTE")

        assertTrue(result.isError())
        assertEquals(Failure.NotFound, (result as Result.Error).failure)
    }

    @Test
    fun `downloadOrderItems - header eliminado retorna ValidationError ORDER_DELETED`() = runBlocking {
        val orderId = "ORDER-DELETED"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, isDeleted = true)

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue(result.isError())
        val failure = (result as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals("ORDER_DELETED", (failure as Failure.ValidationError).message)
    }

    @Test
    fun `downloadOrderItems - header propio retorna ValidationError OWN_ORDER_EXCLUDED`() = runBlocking {
        val orderId = "ORDER-PROPIO"
        localStore[orderId] = makeHeader(orderId, vendedorId = myUid)

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue(result.isError())
        val failure = (result as Result.Error).failure
        assertTrue(failure is Failure.ValidationError)
        assertEquals("OWN_ORDER_EXCLUDED", (failure as Failure.ValidationError).message)
    }

    @Test
    fun `downloadOrderItems - COMPLETED idempotente no vuelve a descargar`() = runBlocking {
        val orderId = "ORDER-DONE"
        localStore[orderId] = makeHeader(
            orderId, vendedorId = otherUid,
            downloadStatus  = "COMPLETED",
            itemsCount      = 2,
            itemsDownloaded = 2,
        )

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue(result.isSuccess())
        assertFalse("commitItems NO debe llamarse para pedido ya COMPLETED", commitCalled.contains(orderId))
    }

    @Test
    fun `downloadOrderItems - remoto vacío resulta en FAILED`() = runBlocking {
        val orderId = "ORDER-EMPTY"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, itemsCount = 3)
        remoteItemsToReturn = emptyList()

        val repo = buildRepository(uid = myUid)
        repo.downloadOrderItems(orderId)

        assertEquals("Status debe ser FAILED", "FAILED", localStore[orderId]?.downloadStatus)
        assertFalse("commitItems NO debe haberse llamado", commitCalled.contains(orderId))
    }

    @Test
    fun `downloadOrderItems - fallo de red retorna NetworkError y deja FAILED`() = runBlocking {
        val orderId = "ORDER-NETFAIL"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, itemsCount = 2)
        remoteThrows = RuntimeException("Sin conexión")

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue(result.isError())
        assertEquals(Failure.NetworkError, (result as Result.Error).failure)
        assertEquals("Status debe ser FAILED", "FAILED", localStore[orderId]?.downloadStatus)
    }

    @Test
    fun `downloadOrderItems - reintento tras FAILED con items correctos resulta en COMPLETED`() = runBlocking {
        val orderId = "ORDER-RETRY"
        localStore[orderId] = makeHeader(
            orderId, vendedorId = otherUid,
            downloadStatus = "FAILED",
            itemsCount     = 2,
        )
        remoteItemsToReturn = listOf(makeRemoteItem("P1"), makeRemoteItem("P2"))

        val repo = buildRepository(uid = myUid)
        val result = repo.downloadOrderItems(orderId)

        assertTrue(result.isSuccess())
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(2, finalItemsStore[orderId]?.size)
    }

    @Test
    fun `downloadOrderItems - total calculado correctamente (precio x cantidad)`() = runBlocking {
        val orderId = "ORDER-TOTAL"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, itemsCount = 2)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("i1", "P1", "Prod 1", unitPrice = 25.0, quantity = 3),
            OrderRemoteDataSource.OrderItemDto("i2", "P2", "Prod 2", unitPrice = 10.0, quantity = 2),
        )

        val repo = buildRepository(uid = myUid)
        repo.downloadOrderItems(orderId)

        // total = 25*3 + 10*2 = 75 + 20 = 95
        assertEquals("Total debe ser 95.0", 95.0, localStore[orderId]?.totalAmount)
    }

    @Test
    fun `downloadOrderItems - items duplicados por productId se deduplicaron (suma qty)`() = runBlocking {
        val orderId = "ORDER-DEDUP"
        localStore[orderId] = makeHeader(orderId, vendedorId = otherUid, itemsCount = 3)
        remoteItemsToReturn = listOf(
            makeRemoteItem("P1", qty = 2),
            makeRemoteItem("P1", qty = 3), // duplicado → se suma: qty = 5
            makeRemoteItem("P2", qty = 1),
        )

        val repo = buildRepository(uid = myUid)
        repo.downloadOrderItems(orderId)

        // 2 productos únicos: P1 (qty=5) y P2 (qty=1)
        assertEquals("Debe haber 2 items únicos en finalItems", 2, finalItemsStore[orderId]?.size)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
    }
}

