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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regresión Bug #2 — deleteOwnHeaders debe hacer soft-delete (isDeleted=1),
 * NO borrado físico, para preservar pedidos ya COMPLETED con items descargados.
 *
 * Antes del fix: DELETE FROM orders WHERE routeId=… AND vendedorId=…
 *   → Pedido COMPLETED borrado de Room, items descargados perdidos.
 *
 * Después del fix: UPDATE orders SET isDeleted=1 WHERE routeId=… AND vendedorId=…
 *   → Pedido permanece en Room con isDeleted=true; no aparece en listas públicas
 *     (getByRouteAndDate filtra isDeleted=0) ni en futuras descargas.
 */
class DeleteOwnHeadersSoftDeleteTest {

    private val myUid = "uid-propio-softdelete"
    private val routeId = "ROUTE-SD"
    private val deliveryDate = "2026-02-22"

    /** Almacén mutable que simula la tabla orders de Room. */
    private val localStore = mutableMapOf<String, OrderEntity>()

    /** Registra las llamadas a deleteOwnHeaders con sus parámetros. */
    data class PurgeCall(val routeId: String, val deliveryDate: String, val vendedorId: String, val now: Long)
    private val purgeCalls = mutableListOf<PurgeCall>()

    private var remoteHeaders: List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()

    @Before
    fun setUp() {
        localStore.clear()
        purgeCalls.clear()
        remoteHeaders = emptyList()
    }

    private fun buildRepo(uid: String? = myUid): OfflineFirstOrderRepository {
        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) { localStore[entity.orderId] = entity }

            override suspend fun deleteOwnHeaders(
                routeId: String, deliveryDate: String, vendedorId: String, now: Long,
            ) {
                purgeCalls.add(PurgeCall(routeId, deliveryDate, vendedorId, now))
                // Simula el UPDATE soft-delete del DAO corregido
                localStore.entries
                    .filter { e ->
                        e.value.routeId == routeId &&
                        e.value.deliveryDate == deliveryDate &&
                        e.value.vendedorId == vendedorId
                    }
                    .forEach { localStore[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }

            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
                localStore.entries
                    .filter { e -> e.value.routeId == routeId && e.value.vendedorId == vendedorId }
                    .forEach { localStore[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }

            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                val c = localStore[orderId] ?: return
                localStore[orderId] = c.copy(isDeleted = true, updatedAt = now)
            }

            override suspend fun deleteItemsByOrderId(orderId: String) {}
            override suspend fun getOrderById(orderId: String) = localStore[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted }
            override suspend fun markInProgress(orderId: String, now: Long) {}
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {}
            override suspend fun clearStaging(orderId: String) {}
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {}
            override suspend fun countStaging(orderId: String) = 0
            override suspend fun getStaging(orderId: String) = emptyList<OrderItemStagingEntity>()
            override suspend fun commitItems(
                orderId: String, finalItems: List<OrderItemEntity>,
                totalAmount: Double, itemsDownloaded: Int, now: Long,
            ) {}
            override fun observeByRoute(routeId: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && !it.isDeleted })

            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<com.are.distribuidora.orders.data.local.entity.OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(localStore.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted })

            override suspend fun getItemsByOrderId(orderId: String): List<com.are.distribuidora.orders.data.local.entity.OrderItemEntity> =
                emptyList()
        }

        val fakeRemote = object : OrderRemoteDataSource {
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = remoteHeaders
            override suspend fun fetchAllOrderHeaders(routeId: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
            override suspend fun fetchOrderItems(routeId: String, orderId: String) =
                emptyList<OrderRemoteDataSource.OrderItemDto>()
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {}
        }

        return OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get() = uid },
        )
    }

    private fun makeStoredOrder(
        orderId: String,
        downloadStatus: String = "COMPLETED",
        itemsDownloaded: Int = 3,
        totalAmount: Double? = 99.0,
    ) = OrderEntity(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = deliveryDate,
        clientName = "Mi Cliente",
        clientAddress = null,
        sellerName = null,
        itemsCount = 3,
        itemsDownloaded = itemsDownloaded,
        totalAmount = totalAmount,
        downloadStatus = downloadStatus,
        failedReasonCode = null,
        failedReasonMessage = null,
        failedAttempts = 0,
        lastAttemptAt = null,
        createdAt = 1000L,
        updatedAt = 2000L,
        vendedorId = myUid, // pedido propio
    )

    private fun makeRemoteHeader(orderId: String, vendedorId: String? = "otro-uid", itemsCount: Int = 2) =
        OrderRemoteDataSource.OrderHeaderDto(
            orderId = orderId,
            routeId = routeId,
            deliveryDate = deliveryDate,
            clientName = "Cliente $orderId",
            clientAddress = null,
            sellerName = null,
            itemsCount = itemsCount,
            vendedorId = vendedorId,
        )

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test A
    // Pedido COMPLETED propio: tras fetchOrdersHeader queda con isDeleted=true
    // pero NO se borra físicamente de Room (los datos de items siguen accesibles)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `pedido propio COMPLETED queda con isDeleted=true no borrado fisicamente`() = runBlocking {
        val ownId = "ORDER-PROPIO-COMPLETED"
        localStore[ownId] = makeStoredOrder(ownId, downloadStatus = "COMPLETED", itemsDownloaded = 3)

        // El remoto trae un pedido ajeno (el propio ya fue filtrado en origen por Opción B)
        remoteHeaders = listOf(makeRemoteHeader("ORDER-AJENO"))

        val repo = buildRepo()
        val result = repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("Expected Success", result is Result.Success)

        // El pedido propio debe SEGUIR EN EL STORE (soft-delete, no borrado físico)
        assertNotNull("Pedido COMPLETED propio debe seguir en Room", localStore[ownId])

        // Y debe tener isDeleted=true
        assertTrue(
            "Pedido COMPLETED propio debe tener isDeleted=true tras la purga",
            localStore[ownId]?.isDeleted == true,
        )

        // El downloadStatus y itemsDownloaded no deben haberse corrompido
        assertEquals(
            "downloadStatus no debe cambiar por la purga",
            "COMPLETED",
            localStore[ownId]?.downloadStatus,
        )
        assertEquals(
            "itemsDownloaded no debe cambiar por la purga",
            3,
            localStore[ownId]?.itemsDownloaded,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test B
    // Pedido propio ITEMS_PENDING: también queda soft-deleted (no borra físico)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `pedido propio ITEMS_PENDING queda con isDeleted=true`() = runBlocking {
        val ownId = "ORDER-PROPIO-PENDING"
        localStore[ownId] = makeStoredOrder(ownId, downloadStatus = "ITEMS_PENDING", itemsDownloaded = 0, totalAmount = null)

        remoteHeaders = listOf(makeRemoteHeader("ORDER-AJENO-2"))
        val repo = buildRepo()
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertNotNull("Pedido debe seguir en Room", localStore[ownId])
        assertTrue("isDeleted debe ser true", localStore[ownId]?.isDeleted == true)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test C
    // Los pedidos soft-deleted NO aparecen en getOrdersByRouteAndDate (filtro isDeleted=0)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `pedidos con isDeleted=true no aparecen en la lista publica`() = runBlocking {
        val ownId = "ORDER-PROPIO-HIDDEN"
        localStore[ownId] = makeStoredOrder(ownId)

        remoteHeaders = listOf(makeRemoteHeader("ORDER-AJENO-3"))
        val repo = buildRepo()
        repo.fetchOrdersHeader(routeId, deliveryDate)

        val publicList = repo.getOrdersByRouteAndDate(routeId, deliveryDate)

        val ids = publicList.map { it.orderId }
        assertFalse("Pedido propio con isDeleted=true no debe aparecer en lista pública", ownId in ids)
        assertTrue("Pedido ajeno debe aparecer en lista pública", "ORDER-AJENO-3" in ids)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test D
    // Múltiples pedidos propios: todos quedan soft-deleted
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `multiples pedidos propios quedan soft-deleted sin borrado fisico`() = runBlocking {
        val ownIds = listOf("OWN-1", "OWN-2", "OWN-3")
        ownIds.forEach { localStore[it] = makeStoredOrder(it) }

        remoteHeaders = listOf(makeRemoteHeader("ORDER-AJENO-MULTI"))
        val repo = buildRepo()
        repo.fetchOrdersHeader(routeId, deliveryDate)

        ownIds.forEach { id ->
            assertNotNull("$id debe seguir en Room", localStore[id])
            assertTrue("$id debe tener isDeleted=true", localStore[id]?.isDeleted == true)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test E
    // Sin uid (sesión cerrada): deleteOwnHeaders NO se llama
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `sin uid deleteOwnHeaders no se invoca y nada se marca isDeleted`() = runBlocking {
        val ownId = "ORDER-NULL-UID"
        localStore[ownId] = makeStoredOrder(ownId).copy(vendedorId = "cualquier-uid")

        remoteHeaders = listOf(makeRemoteHeader("ORDER-X", vendedorId = "cualquier-uid"))
        val repo = buildRepo(uid = null)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("deleteOwnHeaders NO debe llamarse sin uid", purgeCalls.isEmpty())
        assertFalse("El pedido no debe estar soft-deleted sin uid", localStore[ownId]?.isDeleted == true)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Regresión Bug #2 — Test F
    // Verificar que deleteOwnHeaders se invoca con los parámetros correctos
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `deleteOwnHeaders se invoca con routeId deliveryDate y vendedorId correctos`() = runBlocking {
        remoteHeaders = listOf(makeRemoteHeader("ORDER-Z"))
        val repo = buildRepo(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertEquals("deleteOwnHeaders debe haberse llamado 1 vez", 1, purgeCalls.size)
        assertEquals(routeId, purgeCalls[0].routeId)
        assertEquals(deliveryDate, purgeCalls[0].deliveryDate)
        assertEquals(myUid, purgeCalls[0].vendedorId)
        assertTrue("now debe ser un timestamp positivo", purgeCalls[0].now > 0L)
    }
}


