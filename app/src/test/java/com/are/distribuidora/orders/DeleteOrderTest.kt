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
 * Tests unitarios del soft delete de pedidos.
 *
 * Cubre:
 * 1. deleteOrder → pedido marcado isDeleted + items borrados + éxito remoto → Result.Success
 * 2. deleteOrder → fallo remoto → Result.Error(NetworkError), local NO se toca
 * 3. fetchOrdersHeader → headers remotos con isDeleted=true → no se persisten + local purgado
 * 4. fetchOrdersHeader → [propio], [otro eliminado], [otro activo] → solo activo persiste
 * 5. downloadOrderItems → order.isDeleted=true → ORDER_DELETED sin tocar staging
 */
class DeleteOrderTest {

    private val routeId = "ROUTE-DEL"
    private val myUid = "uid-test-del"

    // ── Mutable stores para fake local ────────────────────────────────────────
    private val localStore = mutableMapOf<String, OrderEntity>()
    private val finalItemsStore = mutableMapOf<String, MutableList<OrderItemEntity>>()
    private val stagingStore = mutableMapOf<String, MutableList<OrderItemStagingEntity>>()

    // Registro de llamadas para aserciones
    private val markDeletedCalls = mutableListOf<String>()
    private val deleteItemsCalls = mutableListOf<String>()

    // Estado del remoto
    private var remoteMarkDeletedShouldThrow: Exception? = null
    private var remoteHeaders: List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()
    private var remoteMarkDeletedCalls = mutableListOf<Triple<String, String, String?>>()

    @Before
    fun setUp() {
        localStore.clear()
        finalItemsStore.clear()
        stagingStore.clear()
        markDeletedCalls.clear()
        deleteItemsCalls.clear()
        remoteMarkDeletedCalls.clear()
        remoteMarkDeletedShouldThrow = null
        remoteHeaders = emptyList()
    }

    // ── Builders de fakes ────────────────────────────────────────────────────

    private fun buildFakeLocal() = object : OrderLocalDataSource {
        override suspend fun upsertOrderHeader(entity: OrderEntity) {
            localStore[entity.orderId] = entity
        }
        override suspend fun getOrderById(orderId: String): OrderEntity? = localStore[orderId]
        override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
            localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted }
        override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {
            localStore.entries
                .filter { e -> e.value.routeId == routeId && e.value.deliveryDate == deliveryDate && e.value.vendedorId == vendedorId }
                .forEach { localStore[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
        }
        override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
            localStore.entries
                .filter { e -> e.value.routeId == routeId && e.value.vendedorId == vendedorId }
                .forEach { localStore[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
        }
        override suspend fun markOrderDeleted(orderId: String, now: Long) {
            markDeletedCalls.add(orderId)
            val current = localStore[orderId] ?: return
            localStore[orderId] = current.copy(isDeleted = true, updatedAt = now)
        }
        override suspend fun deleteItemsByOrderId(orderId: String) {
            deleteItemsCalls.add(orderId)
            finalItemsStore.remove(orderId)
            stagingStore.remove(orderId)
        }
        override suspend fun markInProgress(orderId: String, now: Long) {
            val c = localStore[orderId] ?: return
            localStore[orderId] = c.copy(downloadStatus = "IN_PROGRESS", updatedAt = now)
        }
        override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {
            val c = localStore[orderId] ?: return
            localStore[orderId] = c.copy(downloadStatus = "FAILED", failedReasonCode = reasonCode, updatedAt = now)
        }
        override suspend fun clearStaging(orderId: String) { stagingStore[orderId]?.clear() }
        override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {
            items.forEach { stagingStore.getOrPut(it.orderId) { mutableListOf() }.add(it) }
        }
        override suspend fun countStaging(orderId: String): Int = stagingStore[orderId]?.size ?: 0
        override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> = stagingStore[orderId] ?: emptyList()
        override suspend fun commitItems(
            orderId: String, finalItems: List<OrderItemEntity>,
            totalAmount: Double, itemsDownloaded: Int, now: Long,
        ) {
            finalItemsStore[orderId] = finalItems.toMutableList()
            val c = localStore[orderId] ?: return
            localStore[orderId] = c.copy(downloadStatus = "COMPLETED", itemsDownloaded = itemsDownloaded, updatedAt = now)
        }
        override fun observeByRoute(routeId: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
            kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && !it.isDeleted })

        override fun observeByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
            kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })

        override suspend fun getItemsByOrderId(orderId: String): List<com.are.distribuidora.orders.data.local.entity.OrderItemEntity> =
            finalItemsStore[orderId] ?: emptyList()
    }

    private fun buildFakeRemote() = object : OrderRemoteDataSource {
        override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = remoteHeaders
        override suspend fun fetchAllOrderHeaders(routeId: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
        override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> = emptyList()
        override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {
            remoteMarkDeletedCalls.add(Triple(routeId, orderId, deletedByUid))
            remoteMarkDeletedShouldThrow?.let { throw it }
        }
    }

    private fun buildRepo(uid: String? = myUid) = OfflineFirstOrderRepository(
        local = buildFakeLocal(),
        remote = buildFakeRemote(),
        currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = uid },
    )

    private fun makeOrderEntity(
        orderId: String,
        isDeleted: Boolean = false,
        downloadStatus: String = "ITEMS_PENDING",
    ) = OrderEntity(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = "2026-02-21",
        clientName = "Cliente Test",
        clientAddress = null,
        sellerName = null,
        itemsCount = 2,
        itemsDownloaded = 0,
        totalAmount = null,
        downloadStatus = downloadStatus,
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts = 0,
        lastAttemptAt = null,
        createdAt = 1000L,
        updatedAt = 2000L,
        vendedorId = "otro-uid",
        isDeleted = isDeleted,
    )

    private fun makeHeader(
        orderId: String,
        vendedorId: String? = "otro-uid",
        isDeleted: Boolean = false,
        itemsCount: Int = 2,
    ) = OrderRemoteDataSource.OrderHeaderDto(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = "2026-02-21",
        clientName = "Cliente $orderId",
        clientAddress = null,
        sellerName = null,
        itemsCount = itemsCount,
        vendedorId = vendedorId,
        isDeleted = isDeleted,
    )

    // ══════════════════════════════════════════════════════════════════════════
    // deleteOrder: flujo exitoso
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteOrder exitoso marca remoto y local como eliminado y borra items`() = runBlocking {
        val orderId = "ORDER-TO-DELETE"
        localStore[orderId] = makeOrderEntity(orderId)
        // Pre-poblar items finales y staging
        finalItemsStore[orderId] = mutableListOf()
        stagingStore[orderId] = mutableListOf()

        val repo = buildRepo()
        val result = repo.deleteOrder(routeId = routeId, orderId = orderId)

        // Resultado: éxito
        assertTrue("Expected Success but got $result", result is Result.Success)

        // Remoto recibió la llamada con los parámetros correctos
        assertEquals(1, remoteMarkDeletedCalls.size)
        assertEquals(routeId, remoteMarkDeletedCalls[0].first)
        assertEquals(orderId, remoteMarkDeletedCalls[0].second)
        assertEquals(myUid, remoteMarkDeletedCalls[0].third)

        // Local fue marcado como eliminado
        assertTrue("markOrderDeleted debe haberse llamado", markDeletedCalls.contains(orderId))
        assertTrue("local isDeleted debe ser true", localStore[orderId]?.isDeleted == true)

        // Items locales fueron borrados
        assertTrue("deleteItemsByOrderId debe haberse llamado", deleteItemsCalls.contains(orderId))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteOrder: fallo remoto → no se toca el local
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteOrder con fallo remoto retorna NetworkError y no modifica local`() = runBlocking {
        val orderId = "ORDER-REMOTE-FAIL"
        localStore[orderId] = makeOrderEntity(orderId)
        remoteMarkDeletedShouldThrow = RuntimeException("Firestore no disponible")

        val repo = buildRepo()
        val result = repo.deleteOrder(routeId = routeId, orderId = orderId)

        assertTrue("Expected Error", result is Result.Error)
        assertEquals(Failure.NetworkError, (result as Result.Error).failure)

        // Local NO debe haber sido tocado
        assertFalse("markOrderDeleted NO debe haberse llamado", markDeletedCalls.contains(orderId))
        assertFalse("local isDeleted debe seguir false", localStore[orderId]?.isDeleted == true)
        assertFalse("deleteItemsByOrderId NO debe haberse llamado", deleteItemsCalls.contains(orderId))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteOrder: validaciones
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `deleteOrder con routeId vacio retorna ValidationError`() = runBlocking {
        val result = buildRepo().deleteOrder(routeId = "", orderId = "ORDER-1")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).failure is Failure.ValidationError)
    }

    @Test
    fun `deleteOrder con orderId vacio retorna ValidationError`() = runBlocking {
        val result = buildRepo().deleteOrder(routeId = routeId, orderId = "")
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).failure is Failure.ValidationError)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fetchOrdersHeader: filtro isDeleted + Opción B combinados
    // Caso: [propio], [otro eliminado], [otro activo] → solo activo persiste
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `fetchHeaders propio y eliminado excluidos, solo activo persiste`() = runBlocking {
        remoteHeaders = listOf(
            makeHeader("ORDER-PROPIO",   vendedorId = myUid,      isDeleted = false), // excluido por Opción B
            makeHeader("ORDER-DELETED",  vendedorId = "otro-uid", isDeleted = true),  // excluido por isDeleted
            makeHeader("ORDER-ACTIVE",   vendedorId = "otro-uid", isDeleted = false), // incluido
        )

        val repo = buildRepo(uid = myUid)
        val result = repo.fetchOrdersHeader(routeId = routeId, deliveryDate = "2026-02-21")

        assertTrue("Expected Success", result is Result.Success)

        // Solo ORDER-ACTIVE debe persistirse
        assertFalse("ORDER-PROPIO no debe estar en local", localStore.containsKey("ORDER-PROPIO"))
        assertFalse("ORDER-DELETED no debe estar en local", localStore.containsKey("ORDER-DELETED"))
        assertTrue("ORDER-ACTIVE debe estar en local", localStore.containsKey("ORDER-ACTIVE"))
        assertEquals(1, localStore.size)
    }

    @Test
    fun `fetchHeaders pedido eliminado remotamente purga local existente`() = runBlocking {
        val orderId = "ORDER-DELETED-LOCAL"
        // Pre-existente en Room (sin eliminar)
        localStore[orderId] = makeOrderEntity(orderId, isDeleted = false)
        finalItemsStore[orderId] = mutableListOf()

        // Remoto lo devuelve como eliminado
        remoteHeaders = listOf(
            makeHeader(orderId, vendedorId = "otro-uid", isDeleted = true),
        )

        val repo = buildRepo(uid = myUid)
        repo.fetchOrdersHeader(routeId = routeId, deliveryDate = "2026-02-21")

        // El local debe haberse marcado como eliminado
        assertTrue("Local debe quedar isDeleted=true", localStore[orderId]?.isDeleted == true)
        // Los items locales deben haberse borrado
        assertTrue("deleteItemsByOrderId debe haberse llamado", deleteItemsCalls.contains(orderId))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // downloadOrderItems: guard rail isDeleted
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `downloadOrderItems con order isDeleted retorna ORDER_DELETED sin tocar staging`() = runBlocking {
        val orderId = "ORDER-DELETED-GUARD"
        localStore[orderId] = makeOrderEntity(orderId, isDeleted = true, downloadStatus = "ITEMS_PENDING")
        stagingStore[orderId] = mutableListOf() // staging vacío

        val repo = buildRepo()
        val result = repo.downloadOrderItems(orderId)

        assertTrue("Expected Error", result is Result.Error)
        val failure = (result as Result.Error).failure
        assertTrue("Expected ValidationError", failure is Failure.ValidationError)
        assertEquals("ORDER_DELETED", (failure as Failure.ValidationError).message)

        // NO debe haberse marcado IN_PROGRESS
        assertFalse("downloadStatus NO debe ser IN_PROGRESS", localStore[orderId]?.downloadStatus == "IN_PROGRESS")
        // Staging no debe haber sido modificado
        assertEquals(0, stagingStore[orderId]?.size ?: 0)
    }

    @Test
    fun `downloadOrderItems con order isDeleted no llama al remoto`() = runBlocking {
        val orderId = "ORDER-DELETED-NO-REMOTE"
        localStore[orderId] = makeOrderEntity(orderId, isDeleted = true)

        val repo = buildRepo()
        repo.downloadOrderItems(orderId)

        // El remoto NO debe haber sido llamado para fetchOrderItems (verificado implícitamente:
        // si lo hubiera llamado y el fake devuelve emptyList, el estado sería FAILED, no ITEMS_PENDING)
        assertEquals("ITEMS_PENDING", localStore[orderId]?.downloadStatus)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getOrdersByRouteAndDate: excluye eliminados (delegado al DAO)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `getOrdersByRouteAndDate no incluye pedidos eliminados`() = runBlocking {
        val deliveryDate = "2026-02-21"
        localStore["ORDER-OK"]  = makeOrderEntity("ORDER-OK", isDeleted = false).copy(deliveryDate = deliveryDate)
        localStore["ORDER-DEL"] = makeOrderEntity("ORDER-DEL", isDeleted = true).copy(deliveryDate = deliveryDate)

        val repo = buildRepo()
        val orders = repo.getOrdersByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate)

        assertEquals("Solo pedidos no eliminados deben retornarse", 1, orders.size)
        assertEquals("ORDER-OK", orders.first().orderId)
    }
}



