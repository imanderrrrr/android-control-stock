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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regresión Bug #3 — failedAttempts solo debe incrementarse en markFailed, nunca en markInProgress.
 *
 * Antes del fix: markInProgress hacía failedAttempts+1 antes de saber si iba a fallar.
 * Resultado: un pedido descargado correctamente en el primer intento quedaba con failedAttempts=1.
 *
 * Después del fix: el contador solo sube en markFailed (fallo confirmado).
 * - Descarga exitosa primer intento  → failedAttempts=0
 * - Un fallo + reintento exitoso     → failedAttempts=1
 * - Dos fallos                       → failedAttempts=2
 */
class FailedAttemptsCounterTest {

    private val localStore = mutableMapOf<String, OrderEntity>()
    private val stagingStore = mutableMapOf<String, MutableList<OrderItemStagingEntity>>()
    private val finalItemsStore = mutableMapOf<String, MutableList<OrderItemEntity>>()

    private var remoteItemsToReturn: List<OrderRemoteDataSource.OrderItemDto> = emptyList()
    private var remoteThrows: Exception? = null

    private lateinit var repository: OfflineFirstOrderRepository

    @Before
    fun setUp() {
        localStore.clear()
        stagingStore.clear()
        finalItemsStore.clear()
        remoteItemsToReturn = emptyList()
        remoteThrows = null

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
                val current = localStore[orderId] ?: return
                stagingStore.getOrPut(orderId) { mutableListOf() }.clear()
                // Fix verificado: NO incrementar failedAttempts aquí
                localStore[orderId] = current.copy(
                    downloadStatus = "IN_PROGRESS",
                    failedReasonCode = null,
                    failedReasonMessage = null,
                    lastAttemptAt = now,
                    updatedAt = now,
                )
            }

            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
                val current = localStore[orderId] ?: return
                // Fix verificado: SÍ incrementar failedAttempts aquí
                localStore[orderId] = current.copy(
                    downloadStatus = "FAILED",
                    failedReasonCode = reasonCode,
                    failedReasonMessage = reasonMessage,
                    failedAttempts = current.failedAttempts + 1,
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
            override fun observeByRoute(routeId: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && !it.isDeleted })

            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })

            override suspend fun getItemsByOrderId(orderId: String): List<com.are.distribuidora.orders.data.local.entity.OrderItemEntity> =
                emptyList()
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

        repository = OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
    }

    private fun makeHeader(orderId: String, itemsCount: Int, failedAttempts: Int = 0) = OrderEntity(
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
        quantity = 1,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #3 — Test A
    // Descarga exitosa en primer intento: failedAttempts debe quedar en 0
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `descarga exitosa en primer intento no incrementa failedAttempts`() = runBlocking {
        val orderId = "ORDER-SUCCESS-FIRST"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 2)
        remoteItemsToReturn = (1..2).map { makeItemDto(it) }

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Debe ser Success", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(
            "failedAttempts debe ser 0 tras descarga exitosa en primer intento",
            0,
            localStore[orderId]?.failedAttempts,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #3 — Test B
    // Fallo de red: failedAttempts debe incrementarse exactamente en 1
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `fallo de red incrementa failedAttempts exactamente en 1`() = runBlocking {
        val orderId = "ORDER-NET-FAIL"
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 2, failedAttempts = 0)
        remoteThrows = java.io.IOException("Simulated network failure")

        repository.downloadOrderItems(orderId)

        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertEquals(
            "failedAttempts debe ser 1 tras un fallo de red",
            1,
            localStore[orderId]?.failedAttempts,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #3 — Test C
    // Dos fallos acumulados: failedAttempts debe ser 2
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `dos fallos acumulan failedAttempts correctamente`() = runBlocking {
        val orderId = "ORDER-TWO-FAILS"
        // Pedido que ya tuvo 1 fallo previo
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 2, failedAttempts = 1)
            .copy(downloadStatus = "FAILED", failedReasonCode = "NETWORK")
        remoteThrows = java.io.IOException("Second network failure")

        repository.downloadOrderItems(orderId)

        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertEquals(
            "failedAttempts debe acumularse: 1 previo + 1 nuevo = 2",
            2,
            localStore[orderId]?.failedAttempts,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #3 — Test D
    // Fallo → reintento exitoso: failedAttempts queda en 1 (solo el fallo previo)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `reintento exitoso tras un fallo mantiene failedAttempts del fallo previo`() = runBlocking {
        val orderId = "ORDER-RETRY-OK"
        // Pedido que tuvo 1 fallo previo
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 2, failedAttempts = 1)
            .copy(downloadStatus = "FAILED", failedReasonCode = "NETWORK")
        remoteItemsToReturn = (1..2).map { makeItemDto(it) }
        remoteThrows = null // ahora sí funciona la red

        val result = repository.downloadOrderItems(orderId)

        assertTrue("Debe ser Success en el reintento", result is Result.Success)
        assertEquals("COMPLETED", localStore[orderId]?.downloadStatus)
        assertEquals(
            "failedAttempts no debe incrementarse en un reintento exitoso (queda en 1 del fallo previo)",
            1,
            localStore[orderId]?.failedAttempts,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #3 — Test E
    // COUNT_MISMATCH también debe incrementar failedAttempts
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `COUNT_MISMATCH incrementa failedAttempts`() = runBlocking {
        val orderId = "ORDER-MISMATCH-FAIL"
        // Header dice 3 items, pero solo llegan 2 del remoto (sin duplicados)
        localStore[orderId] = makeHeader(orderId = orderId, itemsCount = 3, failedAttempts = 0)
        remoteItemsToReturn = (1..2).map { makeItemDto(it) }

        repository.downloadOrderItems(orderId)

        assertEquals("FAILED", localStore[orderId]?.downloadStatus)
        assertEquals("COUNT_MISMATCH", localStore[orderId]?.failedReasonCode)
        assertEquals(
            "failedAttempts debe ser 1 tras COUNT_MISMATCH",
            1,
            localStore[orderId]?.failedAttempts,
        )
    }
}

