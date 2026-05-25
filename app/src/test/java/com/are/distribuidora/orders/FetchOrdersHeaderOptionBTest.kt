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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios del filtro Opción B en [OfflineFirstOrderRepository.fetchOrdersHeader].
 *
 * Reglas verificadas:
 *  - vendedorId == uid propio  → EXCLUIDO (no se persiste)
 *  - vendedorId == "otro"       → INCLUIDO
 *  - vendedorId == null         → INCLUIDO (legacy/unknown)
 *  - vendedorId == ""           → INCLUIDO (blank = unknown)
 *  - uid == null                → NO filtrar nada (sesión cerrada)
 */
class FetchOrdersHeaderOptionBTest {

    private val routeId = "ROUTE-TEST"
    private val deliveryDate = "2026-02-21"
    private val myUid = "uid-propio-123"

    /** Headers devueltos por el remoto en cada test. */
    private var remoteHeaders: List<OrderRemoteDataSource.OrderHeaderDto> = emptyList()

    /** Almacén de lo que se persiste localmente. */
    private val persisted = mutableMapOf<String, OrderEntity>()

    @Before
    fun setUp() {
        persisted.clear()
        purgeCalls.clear()
        remoteHeaders = emptyList()
    }

    /** Registra las llamadas a deleteOwnHeaders para asertarlas en tests de purga. */
    data class PurgeCall(val routeId: String, val deliveryDate: String, val vendedorId: String)
    private val purgeCalls = mutableListOf<PurgeCall>()

    private fun buildRepository(uid: String?): OfflineFirstOrderRepository {
        val fakeLocal = object : OrderLocalDataSource {
            override suspend fun upsertOrderHeader(entity: OrderEntity) {
                persisted[entity.orderId] = entity
            }
            override suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long) {
                purgeCalls.add(PurgeCall(routeId, deliveryDate, vendedorId))
                // Simula la purga real: marca isDeleted en lugar de borrar físicamente
                persisted.entries
                    .filter {
                        it.value.routeId == routeId &&
                        it.value.deliveryDate == deliveryDate &&
                        it.value.vendedorId == vendedorId
                    }
                    .forEach { persisted[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }
            override suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long) {
                persisted.entries
                    .filter { it.value.routeId == routeId && it.value.vendedorId == vendedorId }
                    .forEach { persisted[it.key] = it.value.copy(isDeleted = true, updatedAt = now) }
            }
            override suspend fun markOrderDeleted(orderId: String, now: Long) {
                val current = persisted[orderId] ?: return
                persisted[orderId] = current.copy(isDeleted = true, updatedAt = now)
            }
            override suspend fun deleteItemsByOrderId(orderId: String) { /* no-op en estos tests */ }
            override suspend fun getOrderById(orderId: String): OrderEntity? = persisted[orderId]
            override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String) =
                persisted.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate }
            override suspend fun markInProgress(orderId: String, now: Long) {}
            override suspend fun markFailed(orderId: String, now: Long, reasonCode: String, reasonMessage: String) {}
            override suspend fun clearStaging(orderId: String) {}
            override suspend fun insertStaging(items: List<OrderItemStagingEntity>) {}
            override suspend fun countStaging(orderId: String): Int = 0
            override suspend fun getStaging(orderId: String): List<OrderItemStagingEntity> = emptyList()
            override suspend fun commitItems(
                orderId: String, finalItems: List<OrderItemEntity>,
                totalAmount: Double, itemsDownloaded: Int, now: Long,
            ) {}
            override fun observeByRoute(routeId: String): kotlinx.coroutines.flow.Flow<List<OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(
                    persisted.values.filter { it.routeId == routeId && !it.isDeleted }
                )

            override fun observeByRouteAndDate(routeId: String, deliveryDate: String): kotlinx.coroutines.flow.Flow<List<OrderEntity>> =
                kotlinx.coroutines.flow.flowOf(
                    persisted.values.filter { it.routeId == routeId && it.deliveryDate == deliveryDate && !it.isDeleted }
                )

            override suspend fun getItemsByOrderId(orderId: String): List<OrderItemEntity> = emptyList()
        }

        val fakeRemote = object : OrderRemoteDataSource {
            override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = remoteHeaders
            override suspend fun fetchAllOrderHeaders(routeId: String) = remoteHeaders
            override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> = emptyList()
            override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) { /* no-op */ }
        }

        val fakeProvider = object : CurrentUserIdProvider {
            override fun get(): String? = uid
        }

        return OfflineFirstOrderRepository(
            local = fakeLocal,
            remote = fakeRemote,
            currentUserIdProvider = fakeProvider,
        )
    }

    private fun makeHeader(orderId: String, vendedorId: String?, itemsCount: Int = 2) =
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
    // Test 1: Solo excluye el pedido propio
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB excluye pedido propio y conserva ajenos y legacy`() = runBlocking {
        persisted.clear()
        remoteHeaders = listOf(
            makeHeader("ORDER-PROPIO",  vendedorId = myUid),   // debe excluirse
            makeHeader("ORDER-OTRO",    vendedorId = "otro-uid-456"), // debe incluirse
            makeHeader("ORDER-NULL",    vendedorId = null),          // debe incluirse
        )

        val repo = buildRepository(uid = myUid)
        val result = repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("Expected Success", result is Result.Success)
        // Pedido propio NO debe estar persistido
        assertFalse("Pedido propio no debe persistirse", persisted.containsKey("ORDER-PROPIO"))
        // Los otros SÍ deben estar
        assertTrue("Pedido de otro debe persistirse", persisted.containsKey("ORDER-OTRO"))
        assertTrue("Pedido legacy (null) debe persistirse", persisted.containsKey("ORDER-NULL"))
        assertEquals(2, persisted.size)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2: vendedorId blank se trata como unknown → incluir
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB conserva pedido con vendedorId blank`() = runBlocking {
        persisted.clear()
        // El DTO viene con vendedorId="" (blank), se trata como unknown → conservar
        // Nota: FirestoreOrderRemoteDataSource ya normaliza blank→null, pero si viene de
        // otra fuente puede llegar blank. El repositorio también debe manejarlo.
        remoteHeaders = listOf(
            makeHeader("ORDER-BLANK", vendedorId = ""),   // incluir
            makeHeader("ORDER-PROPIO", vendedorId = myUid), // excluir
        )

        val repo = buildRepository(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("Pedido blank-vendedorId debe persistirse", persisted.containsKey("ORDER-BLANK"))
        assertFalse("Pedido propio no debe persistirse", persisted.containsKey("ORDER-PROPIO"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3: uid == null → no filtrar nada (sesión cerrada)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB no filtra nada cuando uid es null`() = runBlocking {
        persisted.clear()
        remoteHeaders = listOf(
            makeHeader("ORDER-A", vendedorId = "uid-cualquiera"),
            makeHeader("ORDER-B", vendedorId = null),
        )

        val repo = buildRepository(uid = null)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertEquals("Sin uid, todos los pedidos deben persistirse", 2, persisted.size)
        assertTrue(persisted.containsKey("ORDER-A"))
        assertTrue(persisted.containsKey("ORDER-B"))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 4: vendedorId se persiste correctamente en OrderEntity
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB persiste vendedorId en la entity`() = runBlocking {
        persisted.clear()
        remoteHeaders = listOf(
            makeHeader("ORDER-X", vendedorId = "uid-tercero"),
        )

        val repo = buildRepository(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        val entity = persisted["ORDER-X"]
        assertEquals("uid-tercero", entity?.vendedorId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5: Pedido COMPLETED no se resetea al hacer fetchOrdersHeader
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB no resetea estado COMPLETED al hacer fetch`() = runBlocking {
        val orderId = "ORDER-COMPLETED"
        persisted.clear()
        // Pre-existente como COMPLETED
        persisted[orderId] = OrderEntity(
            orderId = orderId,
            routeId = routeId,
            deliveryDate = deliveryDate,
            clientName = "Cliente viejo",
            clientAddress = null,
            sellerName = null,
            itemsCount = 3,
            itemsDownloaded = 3,
            totalAmount = 150.0,
            downloadStatus = "COMPLETED",
            failedReasonCode = null,
            failedReasonMessage = null,
            failedAttempts = 0,
            lastAttemptAt = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            vendedorId = "uid-tercero",
        )

        remoteHeaders = listOf(
            makeHeader(orderId, vendedorId = "uid-tercero", itemsCount = 3),
        )

        val repo = buildRepository(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        val entity = persisted[orderId]
        assertEquals("COMPLETED", entity?.downloadStatus)
        assertEquals(3, entity?.itemsDownloaded)
        assertEquals(150.0, entity?.totalAmount)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 6: Todos los headers son propios → 0 persistidos, Success
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB con todos pedidos propios devuelve Success y 0 persistidos`() = runBlocking {
        persisted.clear()
        remoteHeaders = listOf(
            makeHeader("ORDER-1", vendedorId = myUid),
            makeHeader("ORDER-2", vendedorId = myUid),
        )

        val repo = buildRepository(uid = myUid)
        val result = repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("Expected Success", result is Result.Success)
        assertTrue("No debe persistirse ningún pedido propio", persisted.isEmpty())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 7: vendedorId null en DTO se guarda como null en entity
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `opcionB vendedorId null en DTO queda null en entity`() = runBlocking {
        remoteHeaders = listOf(
            makeHeader("ORDER-LEGACY", vendedorId = null),
        )

        val repo = buildRepository(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        val entity = persisted["ORDER-LEGACY"]
        assertNull("vendedorId debe ser null para legacy", entity?.vendedorId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 8: PURGA — si Room tenía pedido propio previo, tras fetch queda 0
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `purga elimina pedido propio preexistente en Room antes del fetch`() = runBlocking {
        // Pre-cargar en Room un pedido propio (datos viejos de ejecución anterior)
        val ownOrderId = "ORDER-PROPIO-VIEJO"
        persisted[ownOrderId] = OrderEntity(
            orderId = ownOrderId,
            routeId = routeId,
            deliveryDate = deliveryDate,
            clientName = "Cliente Propio",
            clientAddress = null,
            sellerName = null,
            itemsCount = 2,
            itemsDownloaded = 0,
            totalAmount = null,
            downloadStatus = "ITEMS_PENDING",
            failedReasonCode = null,
            failedReasonMessage = null,
            failedAttempts = 0,
            lastAttemptAt = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            vendedorId = myUid,           // <── dato viejo: es nuestro pedido
        )
        // El remoto ya no devuelve ese pedido propio (correcto: Opción B excluye en origen)
        remoteHeaders = listOf(
            makeHeader("ORDER-AJENO", vendedorId = "otro-uid"),
        )

        val repo = buildRepository(uid = myUid)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        // El pedido propio debe haber sido marcado como isDeleted=true (soft-delete) en Room
        // y NO reaparece en la lista pública (getOrdersByRouteAndDate filtra isDeleted=0).
        val ownEntity = persisted[ownOrderId]
        assertTrue("Pedido propio debe existir en el store (soft-delete, no borrado físico)", ownEntity != null)
        assertTrue("Pedido propio debe tener isDeleted=true", ownEntity?.isDeleted == true)
        // El pedido ajeno sí debe estar activo
        assertTrue("Pedido ajeno debe persistirse", persisted.containsKey("ORDER-AJENO"))
        assertFalse("Pedido ajeno no debe estar eliminado", persisted["ORDER-AJENO"]?.isDeleted == true)

        // Verificar que se llamó a deleteOwnHeaders con los parámetros correctos
        assertEquals("deleteOwnHeaders debe haberse llamado 1 vez", 1, purgeCalls.size)
        assertEquals(routeId, purgeCalls[0].routeId)
        assertEquals(deliveryDate, purgeCalls[0].deliveryDate)
        assertEquals(myUid, purgeCalls[0].vendedorId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 9: uid null → NO se llama a deleteOwnHeaders (sin purga)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `purga no se ejecuta cuando uid es null`() = runBlocking {
        remoteHeaders = listOf(
            makeHeader("ORDER-A", vendedorId = "uid-cualquiera"),
        )

        val repo = buildRepository(uid = null)
        repo.fetchOrdersHeader(routeId, deliveryDate)

        assertTrue("deleteOwnHeaders NO debe llamarse sin uid", purgeCalls.isEmpty())
        assertEquals(1, persisted.size)
    }
}
