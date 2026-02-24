package com.are.distribuidora.orders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.RoomOrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de integración (domain+data) para resiliencia durante sync offline-first de pedidos.
 *
 * Reglas:
 * - Room in-memory (DAOs reales)
 * - Repositorio real (OfflineFirstOrderRepository)
 * - Remote fake controlable (sin Firebase real)
 * - NO tocar código de producción
 */
class OrdersSyncErrorIntegrationTest {

    private lateinit var db: DistribuidoraDatabase
    private val roomExecutor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        roomExecutor.shutdown()
    }

    @Test
    fun E3_downloadOrderItems_whenCommitThrows_doesNotPersistFinalItems_clearsStaging_and_orderNotCompleted() = runBlocking {
        // Arrange
        val orderId = "ORDER_E3"
        val routeId = "ROUTE_A"
        val itemsCount = 3

        insertOrderHeader(orderId = orderId, routeId = routeId, itemsCount = itemsCount)

        // Simula que ya hay staging válido (p.ej. quedó de un intento previo o preparación).
        db.orderItemStagingDao().insertAll(
            listOf(
                OrderItemStagingEntity(itemId = "ITEM-E3-1", orderId = orderId, productId = "P1", productName = "Producto 1", unitPrice = 10.0, quantity = 1),
                OrderItemStagingEntity(itemId = "ITEM-E3-2", orderId = orderId, productId = "P2", productName = "Producto 2", unitPrice = 5.0, quantity = 2),
                OrderItemStagingEntity(itemId = "ITEM-E3-3", orderId = orderId, productId = "P3", productName = "Producto 3", unitPrice = 1.0, quantity = 3),
            )
        )

        val remote = ConfigurableOrderRemoteDataSource(
            mode = ConfigurableOrderRemoteDataSource.Mode.RETURN_VALID_ITEMS,
            validItems = buildValidItems(orderId = orderId, count = itemsCount),
        )

        // Base real + wrapper que fuerza excepción en commit
        val baseLocal = RoomOrderLocalDataSource(
            db = db,
            orderDao = db.orderDao(),
            orderItemDao = db.orderItemDao(),
            stagingDao = db.orderItemStagingDao(),
        )
        val failingCommitLocal = object : OrderLocalDataSource by baseLocal {
            override suspend fun commitItems(
                orderId: String,
                finalItems: List<OrderItemEntity>,
                totalAmount: Double,
                itemsDownloaded: Int,
                now: Long,
            ) {
                throw RuntimeException("Simulated commit failure")
            }
        }

        val repository = OfflineFirstOrderRepository(
            local = failingCommitLocal,
            remote = remote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
        val useCase = DownloadOrderItemsUseCase(repository)

        // Act
        val result = useCase.execute(orderId)

        // Assert
        // Debe fallar sin crashear.
        assertTrue(result is Result.Error)

        // Crítico: no debe haber commit parcial.
        assertEquals(0, db.orderItemDao().getByOrderId(orderId).size)

        // Crítico: staging debe limpiarse para permitir reintento seguro.
        assertEquals(0, db.orderItemStagingDao().countByOrderId(orderId))

        // Crítico: el pedido no debe quedar COMPLETED si el commit falló.
        val updatedOrder = db.orderDao().getById(orderId)
        assertTrue(updatedOrder != null)
        assertTrue(updatedOrder!!.downloadStatus != "COMPLETED")
    }

    @Test
    fun E4_downloadOrderItems_afterStagingLeftovers_cleansStagingOnRetry_and_completes_withoutDuplicates() = runBlocking {
        // Arrange
        val orderId = "ORDER_E4"
        val routeId = "ROUTE_A"
        val itemsCount = 3

        insertOrderHeader(orderId = orderId, routeId = routeId, itemsCount = itemsCount)

        // Simula "app muere" en mitad del sync: quedan restos en staging.
        db.orderItemStagingDao().insertAll(
            listOf(
                OrderItemStagingEntity(itemId = "ITEM-E4-OLD", orderId = orderId, productId = "OLD", productName = "Stale", unitPrice = 99.0, quantity = 99),
            )
        )

        val remote = ConfigurableOrderRemoteDataSource(
            mode = ConfigurableOrderRemoteDataSource.Mode.RETURN_VALID_ITEMS,
            validItems = buildValidItems(orderId = orderId, count = itemsCount),
        )

        val local = RoomOrderLocalDataSource(
            db = db,
            orderDao = db.orderDao(),
            orderItemDao = db.orderItemDao(),
            stagingDao = db.orderItemStagingDao(),
        )

        val repository = OfflineFirstOrderRepository(
            local = local,
            remote = remote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
        val useCase = DownloadOrderItemsUseCase(repository)

        // Act
        val result = useCase.execute(orderId)

        // Assert
        assertTrue(result is Result.Success)

        // Al inicio del intento, staging debe limpiarse (no pueden quedar residuos).
        assertEquals(0, db.orderItemStagingDao().countByOrderId(orderId))

        // Items finales correctos y sin duplicados.
        val finalItems = db.orderItemDao().getByOrderId(orderId)
        assertEquals(itemsCount, finalItems.size)
        assertEquals(itemsCount, finalItems.map { it.productId }.distinct().size)

        // Pedido queda COMPLETED.
        val updatedOrder = db.orderDao().getById(orderId)
        assertTrue(updatedOrder != null)
        assertEquals("COMPLETED", updatedOrder!!.downloadStatus)
        assertEquals(itemsCount, updatedOrder.itemsDownloaded)
    }

    @Test
    fun E5_downloadOrderItems_whenFirstAttemptNetworkFails_thenSecondAttemptSucceeds_and_leavesSystemClean() = runBlocking {
        // Arrange
        val orderId = "ORDER_E5"
        val routeId = "ROUTE_A"
        val itemsCount = 3

        insertOrderHeader(orderId = orderId, routeId = routeId, itemsCount = itemsCount)

        val remote = SequencedOrderRemoteDataSource(
            routeIdExpected = routeId,
            orderIdExpected = orderId,
            first = SequencedOrderRemoteDataSource.Step.ThrowOnFetchItems,
            second = SequencedOrderRemoteDataSource.Step.ReturnItems(buildValidItems(orderId = orderId, count = itemsCount)),
        )

        val local = RoomOrderLocalDataSource(
            db = db,
            orderDao = db.orderDao(),
            orderItemDao = db.orderItemDao(),
            stagingDao = db.orderItemStagingDao(),
        )

        val repository = OfflineFirstOrderRepository(
            local = local,
            remote = remote,
            currentUserIdProvider = object : CurrentUserIdProvider { override fun get(): String? = null },
        )
        val useCase = DownloadOrderItemsUseCase(repository)

        // Act (1): falla red
        val firstResult = useCase.execute(orderId)

        // Assert (1)
        assertTrue(firstResult is Result.Error)
        assertEquals(0, db.orderItemDao().getByOrderId(orderId).size)
        assertEquals(0, db.orderItemStagingDao().countByOrderId(orderId))

        // Act (2): reintento ok
        val secondResult = useCase.execute(orderId)

        // Assert (2)
        assertTrue(secondResult is Result.Success)

        val finalItems = db.orderItemDao().getByOrderId(orderId)
        assertEquals(itemsCount, finalItems.size)
        assertEquals(0, db.orderItemStagingDao().countByOrderId(orderId))

        val updatedOrder = db.orderDao().getById(orderId)
        assertTrue(updatedOrder != null)
        assertEquals("COMPLETED", updatedOrder!!.downloadStatus)
        assertEquals(itemsCount, updatedOrder.itemsDownloaded)
    }

    // -------------------------
    // Helpers mínimos (locales al test; no genéricos)
    // -------------------------

    private suspend fun insertOrderHeader(orderId: String, routeId: String, itemsCount: Int) {
        val now = System.currentTimeMillis()
        db.orderDao().upsert(
            OrderEntity(
                orderId = orderId,
                routeId = routeId,
                deliveryDate = "2026-01-17",
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
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun buildValidItems(orderId: String, count: Int): List<OrderRemoteDataSource.OrderItemDto> {
        // Determinista y coherente con itemsCount.
        return (1..count).map { i ->
            OrderRemoteDataSource.OrderItemDto(
                itemId = "$orderId-ITEM-$i",
                productId = "$orderId-P$i",
                productName = "Producto $i",
                unitPrice = 10.0 + i,
                quantity = 1,
            )
        }
    }

    /**
     * Fake remoto configurable (un solo modo) para simular retorno OK o fallo de red.
     */
    private class ConfigurableOrderRemoteDataSource(
        private val mode: Mode,
        private val validItems: List<OrderRemoteDataSource.OrderItemDto> = emptyList(),
    ) : OrderRemoteDataSource {

        enum class Mode {
            THROW_ON_FETCH_ITEMS,
            RETURN_VALID_ITEMS,
        }

        override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderRemoteDataSource.OrderHeaderDto> =
            emptyList()

        override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
            return when (mode) {
                Mode.THROW_ON_FETCH_ITEMS -> throw RuntimeException("Simulated network error")
                Mode.RETURN_VALID_ITEMS -> validItems
            }
        }
    }

    /**
     * Fake remoto por secuencia de llamadas: primer intento falla y segundo retorna OK.
     * Útil para tests de reintento (E5) sin tocar producción.
     */
    private class SequencedOrderRemoteDataSource(
        private val routeIdExpected: String,
        private val orderIdExpected: String,
        private val first: Step,
        private val second: Step,
    ) : OrderRemoteDataSource {

        sealed class Step {
            data object ThrowOnFetchItems : Step()
            data class ReturnItems(val items: List<OrderRemoteDataSource.OrderItemDto>) : Step()
        }

        private var callCount = 0

        override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderRemoteDataSource.OrderHeaderDto> =
            emptyList()

        override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
            // Validación de contrato del test: asegura que el repo está llamando con ids correctos.
            assertEquals(routeIdExpected, routeId)
            assertEquals(orderIdExpected, orderId)

            callCount += 1
            val step = if (callCount == 1) first else second

            return when (step) {
                is Step.ThrowOnFetchItems -> throw RuntimeException("Simulated network error")
                is Step.ReturnItems -> step.items
            }
        }
    }
}
