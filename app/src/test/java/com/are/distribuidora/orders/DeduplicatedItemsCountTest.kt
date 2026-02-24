package com.are.distribuidora.orders

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeduplicatedItemsCountTest {

    private val localStore = mutableMapOf<String, OrderEntity>()
    private val stagingStore = mutableMapOf<String, MutableList<OrderItemStagingEntity>>()
    private val finalItemsStore = mutableMapOf<String, MutableList<OrderItemEntity>>()
    private val commitCalled = mutableListOf<String>()

    private var remoteItemsToReturn: List<OrderRemoteDataSource.OrderItemDto> = emptyList()

    private lateinit var repository: OfflineFirstOrderRepository

    @Before
    fun setUp() {
        localStore.clear()
        stagingStore.clear()
        finalItemsStore.clear()
        commitCalled.clear()
        remoteItemsToReturn = emptyList()

        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) { localStore[entity.orderId] = entity }
            override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {}
            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {}
            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(isDeleted = true, updatedAt = now)
            }
            override suspend fun deleteItemsByOrderId(orderId: String) {
                finalItemsStore.remove(orderId); stagingStore.remove(orderId)
            }
            override suspend fun getOrderById(orderId: String) = localStore[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate }
            override suspend fun markInProgress(orderId: String, now: Long) {
                val c = localStore[orderId] ?: return
                stagingStore.getOrPut(orderId) { mutableListOf() }.clear()
                localStore[orderId] = c.copy(downloadStatus = "IN_PROGRESS", lastAttemptAt = now, updatedAt = now)
            }
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(
                    downloadStatus = "FAILED",
                    failedReasonCode = reasonCode,
                    failedReasonMessage = reasonMessage,
                    failedAttempts = c.failedAttempts + 1,
                    lastAttemptAt = now,
                    updatedAt = now,
                )
            }
            override suspend fun clearStaging(orderId: String) { stagingStore[orderId]?.clear() }
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
                items.forEach { stagingStore.getOrPut(it.orderId) { mutableListOf() }.add(it) }
            }
            override suspend fun countStaging(orderId: String) = stagingStore[orderId]?.size ?: 0
            override suspend fun getStaging(orderId: String) = stagingStore[orderId]?.toList() ?: emptyList()
            override suspend fun commitItems(
                orderId: String, finalItems: List<OrderItemEntity>,
                totalAmount: Double, itemsDownloaded: Int, now: Long,
            ) {
                commitCalled.add(orderId)
                finalItemsStore[orderId] = finalItems.toMutableList()
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(
                    downloadStatus = "COMPLETED",
                    itemsDownloaded = itemsDownloaded,
                    totalAmount = totalAmount,
                    failedReasonCode = null,
                    failedReasonMessage = null,
                    updatedAt = now,
                )
                stagingStore[orderId]?.clear()
            }
            override fun observeByRoute(routeId: String): kotlinx.coroutines.flow.Flow<List<OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && !it.isDeleted })
            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })
            override suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity> =
                finalItemsStore[orderId] ?: emptyList()
        }

        val fakeRemote = object : OrderRemoteDataSource {
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) =
                emptyList<OrderRemoteDataSource.OrderHeaderDto>()
            override suspend fun fetchAllOrderHeaders(routeId: String) =
                emptyList<OrderRemoteDataSource.OrderHeaderDto>()
            override suspend fun fetchOrderItems(routeId: String, orderId: String) = remoteItemsToReturn
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {}
        }

        repository = OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
    }

    private fun makeHeader(orderId: String, itemsCount: Int) = OrderEntity(
        orderId = orderId,
        routeId = "ROUTE-A",
        deliveryDate = "2026-02-22",
        clientName = "Cliente",
        clientAddress = null,
        sellerName = null,
        itemsCount = itemsCount,
        itemsDownloaded = 0,
        totalAmount = null,
        downloadStatus = "ITEMS_PENDING",
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts = 0,
        lastAttemptAt = null,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    @Test
    fun `duplicados de productId se combinan y no causan COUNT_MISMATCH`() = runBlocking {
        val orderId = "ORDER-DEDUP"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 5)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("ITEM-A1", "PROD-A", "Producto A", 10.0, 2),
            OrderRemoteDataSource.OrderItemDto("ITEM-A2", "PROD-A", "Producto A", 10.0, 3),
            OrderRemoteDataSource.OrderItemDto("ITEM-B",  "PROD-B", "Producto B",  5.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-C",  "PROD-C", "Producto C", 15.0, 4),
            OrderRemoteDataSource.OrderItemDto("ITEM-D",  "PROD-D", "Producto D",  8.0, 2),
        )
        val result = repository.downloadOrderItems(orderId)
        assertTrue("Debe ser Success con duplicados combinados", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertTrue(commitCalled.contains(orderId))
        val finalItems = finalItemsStore[orderId] ?: emptyList()
        assertEquals(4, finalItems.size)
        val prodA = finalItems.firstOrNull { it.productId == "PROD-A" }
        assertEquals(5, prodA?.quantity)
    }

    @Test
    fun `sin duplicados count exacto sigue produciendo COMPLETED`() = runBlocking {
        val orderId = "ORDER-NO-DEDUP"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("ITEM-1", "PROD-1", "P1", 10.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-2", "PROD-2", "P2", 20.0, 2),
            OrderRemoteDataSource.OrderItemDto("ITEM-3", "PROD-3", "P3", 30.0, 3),
        )
        val result = repository.downloadOrderItems(orderId)
        assertTrue(result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(3, finalItemsStore[orderId]?.size)
    }

    @Test
    fun `genuina falta de items sin duplicados sigue produciendo COUNT_MISMATCH`() = runBlocking {
        val orderId = "ORDER-REAL-MISMATCH"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 4)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("ITEM-1", "PROD-1", "P1", 10.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-2", "PROD-2", "P2", 20.0, 2),
        )
        repository.downloadOrderItems(orderId)
        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertEquals("COUNT_MISMATCH", localStore[orderId]?.failedReasonCode)
        assertFalse(commitCalled.contains(orderId))
    }

    @Test
    fun `todos los items son duplicados del mismo productId resulta en 1 item unico COMPLETED`() = runBlocking {
        val orderId = "ORDER-ALL-SAME-PROD"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("ITEM-X1", "PROD-X", "Producto X", 10.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-X2", "PROD-X", "Producto X", 10.0, 2),
            OrderRemoteDataSource.OrderItemDto("ITEM-X3", "PROD-X", "Producto X", 10.0, 4),
        )
        val result = repository.downloadOrderItems(orderId)
        assertTrue(result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        val finalItems = finalItemsStore[orderId] ?: emptyList()
        assertEquals(1, finalItems.size)
        assertEquals(7, finalItems[0].quantity)
    }

    @Test
    fun `itemsDownloaded se actualiza al conteo normalizado no al bruto de Firestore`() = runBlocking {
        val orderId = "ORDER-ITEMS-COUNT"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 5)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto("ITEM-A1", "PROD-A", "Prod A", 10.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-A2", "PROD-A", "Prod A", 10.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-B",  "PROD-B", "Prod B",  5.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-C",  "PROD-C", "Prod C", 15.0, 1),
            OrderRemoteDataSource.OrderItemDto("ITEM-D",  "PROD-D", "Prod D",  8.0, 1),
        )
        repository.downloadOrderItems(orderId)
        val entity = localStore[orderId]
        assertEquals(4, entity?.itemsDownloaded)
    }
}
