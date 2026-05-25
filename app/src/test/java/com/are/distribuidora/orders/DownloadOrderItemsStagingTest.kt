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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios del flujo downloadOrderItems en OfflineFirstOrderRepository.
 *
 * Usa mocks manuales de [OrderLocalDataSource] y [OrderRemoteDataSource].
 *
 * Verifica:
 * 1. stagingCount == itemsCount → COMPLETED (commit se ejecuta)
 * 2. stagingCount != itemsCount → FAILED + COUNT_MISMATCH + no commit
 * 3. Remote vacío → FAILED + sin commit
 * 4. Pedido ya COMPLETED con count correcto → skip (idempotencia)
 * 5. Reintento tras FAILED → si remoto devuelve items correctos → COMPLETED
 */
class DownloadOrderItemsStagingTest {

    private lateinit var repository: OfflineFirstOrderRepository

    // Estado mutable del mock local para simular Room
    private val localStore = mutableMapOf<String, OrderEntity>()
    private val stagingStore = mutableMapOf<String, MutableList<OrderItemStagingEntity>>()
    private val finalItemsStore = mutableMapOf<String, MutableList<OrderItemEntity>>()
    private val commitCalled = mutableListOf<String>()

    private var remoteItemsToReturn: List<OrderRemoteDataSource.OrderItemDto> = emptyList()

    @Before
    fun setUp() {
        localStore.clear()
        stagingStore.clear()
        finalItemsStore.clear()
        commitCalled.clear()
        remoteItemsToReturn = emptyList()

        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) {
                localStore[entity.orderId] = entity
            }

            override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {
                // No-op en estos tests (no se ejercita fetchOrdersHeader aquí)
            }

            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
                // No-op
            }

            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(isDeleted = true, updatedAt = now)
            }

            override suspend fun deleteItemsByOrderId(orderId: String) {
                finalItemsStore.remove(orderId)
                stagingStore.remove(orderId)
            }

            override suspend fun getOrderById(orderId: String): OrderEntity? =
                localStore[orderId]

            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate }

            override suspend fun markInProgress(orderId: String, now: Long) {
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(
                    downloadStatus = "IN_PROGRESS",
                    failedAttempts = current.failedAttempts + 1,
                    lastAttemptAt = now,
                    updatedAt = now,
                )
                stagingStore.getOrPut(orderId) { mutableListOf() }.clear()
            }

            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(
                    downloadStatus = "FAILED",
                    failedReasonCode = reasonCode,
                    failedReasonMessage = reasonMessage,
                    lastAttemptAt = now,
                    updatedAt = now,
                )
            }

            override suspend fun clearStaging(orderId: String) {
                stagingStore[orderId]?.clear()
            }

            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
                items.forEach { item ->
                    stagingStore.getOrPut(item.orderId) { mutableListOf() }.add(item)
                }
            }

            override suspend fun countStaging(orderId: String): Int =
                stagingStore[orderId]?.size ?: 0

            override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> =
                stagingStore[orderId]?.toList() ?: emptyList()

            override suspend fun commitItems(
                orderId: String,
                finalItems: List<OrderItemEntity>,
                totalAmount: Double,
                itemsDownloaded: Int,
                now: Long,
            ) {
                commitCalled.add(orderId)
                finalItemsStore[orderId] = finalItems.toMutableList()
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(
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
            override suspend fun fetchOrderHeaders(
                routeId: String, deliveryDate: String
            ): List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()

            override suspend fun fetchAllOrderHeaders(routeId: String): List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()

            override suspend fun fetchOrderItems(
                routeId: String, orderId: String
            ): List<OrderRemoteDataSource.OrderItemDto> = remoteItemsToReturn

            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) { /* no-op */ }
        }

        repository = OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
    }

    /** Construye un repositorio idéntico al de setUp pero con un uid fijo (para el guard rail). */
    private fun buildRepositoryWithUid(uid: String): OfflineFirstOrderRepository {
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
            override suspend fun getOrderById(orderId: String): OrderEntity? = localStore[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate }
            override suspend fun markInProgress(orderId: String, now: Long) {
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(downloadStatus = "IN_PROGRESS", failedAttempts = current.failedAttempts + 1, lastAttemptAt = now, updatedAt = now)
                stagingStore.getOrPut(orderId) { mutableListOf() }.clear()
            }
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(downloadStatus = "FAILED", failedReasonCode = reasonCode, failedReasonMessage = reasonMessage, lastAttemptAt = now, updatedAt = now)
            }
            override suspend fun clearStaging(orderId: String) { stagingStore[orderId]?.clear() }
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
                items.forEach { item -> stagingStore.getOrPut(item.orderId) { mutableListOf() }.add(item) }
            }
            override suspend fun countStaging(orderId: String): Int = stagingStore[orderId]?.size ?: 0
            override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> = stagingStore[orderId]?.toList() ?: emptyList()
            override suspend fun commitItems(orderId: String, finalItems: List<OrderItemEntity>, totalAmount: Double, itemsDownloaded: Int, now: Long) {
                commitCalled.add(orderId)
                finalItemsStore[orderId] = finalItems.toMutableList()
                val current = localStore[orderId] ?: return
                localStore[orderId] = current.copy(downloadStatus = "COMPLETED", itemsDownloaded = itemsDownloaded, totalAmount = totalAmount, failedReasonCode = null, failedReasonMessage = null, updatedAt = now)
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
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()
            override suspend fun fetchAllOrderHeaders(routeId: String): List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()
            override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> = remoteItemsToReturn
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) { /* no-op */ }
        }
        return OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = uid },
        )
    }

    private fun makeHeader(
        orderId: String,
        routeId: String = "ROUTE-A",
        itemsCount: Int,
        downloadStatus: String = "ITEMS_PENDING",
        failedAttempts: Int = 0,
    ) = OrderEntity(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = "2026-01-17",
        clientName = "Cliente Test",
        clientAddress = null,
        sellerName = null,
        itemsCount = itemsCount,
        itemsDownloaded = 0,
        totalAmount = null,
        downloadStatus = downloadStatus,
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts = failedAttempts,
        lastAttemptAt = null,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private fun makeItemDto(index: Int) = OrderRemoteDataSource.OrderItemDto(
        itemId = "ITEM-$index",
        productId = "PROD-$index",
        productName = "Producto $index",
        unitPrice = 10.0,
        quantity = 2,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1: stagingCount == itemsCount → COMPLETED
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `stagingCount equals itemsCount leads to COMPLETED`() = runBlocking {
        val orderId = "ORDER-OK"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3)
        remoteItemsToReturn = (1..3).map { makeItemDto(it) }

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Expected Success but got $result", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(3, localStore[orderId]?.itemsDownloaded)
        assertTrue("commit should have been called", commitCalled.contains(orderId))
        val finalItems = finalItemsStore[orderId] ?: emptyList()
        assertEquals(3, finalItems.size)
        assertEquals(0, stagingStore[orderId]?.size ?: 0)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2: stagingCount != itemsCount → COUNT_MISMATCH, no commit
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `stagingCount differs from itemsCount causes COUNT_MISMATCH and no commit`() = runBlocking {
        val orderId = "ORDER-MISMATCH"
        // Header dice itemsCount=5, remoto solo devuelve 3
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 5)
        remoteItemsToReturn = (1..3).map { makeItemDto(it) }

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Expected Error but got $result", result is Result.Error)
        val error = result as Result.Error
        assertTrue(error.failure is Failure.ValidationError)
        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertEquals("COUNT_MISMATCH", localStore[orderId]?.failedReasonCode)
        assertTrue("commit should NOT have been called", !commitCalled.contains(orderId))
        assertEquals(0, stagingStore[orderId]?.size ?: 0)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3: Remote devuelve lista vacía → FAILED, sin commit
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `empty remote items causes FAILED and no commit`() = runBlocking {
        val orderId = "ORDER-EMPTY"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3)
        remoteItemsToReturn = emptyList()

        val result = repository.downloadOrderItems(orderId)

        // El repo devuelve Success (deja para reintento) pero marca FAILED
        assertTrue("Expected Success (retry-later) but got $result", result is Result.Success)
        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertTrue("commit should NOT have been called", !commitCalled.contains(orderId))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4: Pedido ya COMPLETED con count correcto → skip (idempotencia)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `already COMPLETED order is skipped idempotently`() = runBlocking {
        val orderId = "ORDER-DONE"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3, downloadStatus = "COMPLETED")
            .copy(itemsDownloaded = 3)

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Expected Success but got $result", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertTrue("commit should NOT have been called on already COMPLETED", !commitCalled.contains(orderId))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5: Reintento tras FAILED → si remoto devuelve items correctos → COMPLETED
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `retry after FAILED succeeds when remote returns correct items`() = runBlocking {
        val orderId = "ORDER-RETRY"
        localStore[orderId] = makeHeader(
            orderId = orderId,
            itemsCount = 2,
            downloadStatus = "FAILED",
            failedAttempts = 1,
        ).copy(failedReasonCode = "NETWORK")

        remoteItemsToReturn = (1..2).map { makeItemDto(it) }

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Expected Success but got $result", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(2, localStore[orderId]?.itemsDownloaded)
        assertTrue("commit should have been called on retry", commitCalled.contains(orderId))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 6: itemId se propaga correctamente al commit
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `itemId from remote is preserved in final items after commit`() = runBlocking {
        val orderId = "ORDER-ITEMID"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 2)
        remoteItemsToReturn = listOf(
            OrderRemoteDataSource.OrderItemDto(itemId = "STABLE-ID-1", productId = "P1", productName = "Prod 1", unitPrice = 5.0, quantity = 1),
            OrderRemoteDataSource.OrderItemDto(itemId = "STABLE-ID-2", productId = "P2", productName = "Prod 2", unitPrice = 8.0, quantity = 3),
        )

        val result = repository.downloadOrderItems(orderId)

        assertTrue(result is Result.Success)
        val finalItems = finalItemsStore[orderId] ?: emptyList()
        assertEquals(2, finalItems.size)
        val itemIds = finalItems.map { it.itemId }.toSet()
        assertTrue("STABLE-ID-1 should be in final items", "STABLE-ID-1" in itemIds)
        assertTrue("STABLE-ID-2 should be in final items", "STABLE-ID-2" in itemIds)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 7: Guard rail — downloadOrderItems retorna ValidationError si es pedido propio
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `guard rail returns ValidationError OWN_ORDER_EXCLUDED when vendedorId equals uid`() = runBlocking {
        val myUid = "uid-propio-guard"
        val orderId = "ORDER-PROPIO-GUARD"

        // Simular pedido propio que quedó en Room por datos viejos
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3)
            .copy(vendedorId = myUid)

        // El remoto tiene items listos (para verificar que NO se llaman)
        remoteItemsToReturn = (1..3).map { makeItemDto(it) }

        val repo = buildRepositoryWithUid(myUid)
        val result = repo.downloadOrderItems(orderId)

        // Debe retornar error de validación con código OWN_ORDER_EXCLUDED
        assertTrue("Expected Error but got $result", result is Result.Error)
        val error = result as Result.Error
        assertTrue("Expected ValidationError", error.failure is Failure.ValidationError)
        assertEquals("OWN_ORDER_EXCLUDED", (error.failure as Failure.ValidationError).message)

        // NO debe haberse marcado IN_PROGRESS ni COMPLETED
        assertFalse("commit should NOT have been called for own order", commitCalled.contains(orderId))
        // El estado local NO debe haber cambiado (no tocar DB)
        val storedOrder = localStore[orderId]
        assertEquals("ITEMS_PENDING", storedOrder?.downloadStatus)
    }
}
