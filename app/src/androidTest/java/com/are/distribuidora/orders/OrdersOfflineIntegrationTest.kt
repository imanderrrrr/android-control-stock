package com.are.distribuidora.orders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.RoomOrderLocalDataSource
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import com.are.distribuidora.orders.domain.usecase.FetchOrdersHeaderUseCase
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrdersOfflineIntegrationTest {

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
    fun fetchHeaders_then_downloadItems_forOneBigOrder_commitsAtomically_and_marksCompleted() = runBlocking {
        // GIVEN
        val fakeRemote = FakeOrderRemoteDataSource()

        val local = RoomOrderLocalDataSource(
            db = db,
            orderDao = db.orderDao(),
            orderItemDao = db.orderItemDao(),
            stagingDao = db.orderItemStagingDao(),
        )

        val repository = OfflineFirstOrderRepository(
            local = local,
            remote = fakeRemote,
        )

        val fetchHeaders = FetchOrdersHeaderUseCase(repository)
        val downloadItems = DownloadOrderItemsUseCase(repository)

        // WHEN: headers only
        val fetchResult = fetchHeaders.execute(
            routeId = "ROUTE_A",
            deliveryDate = "2026-01-17",
        )

        // THEN: headers persisted, no items
        assertTrue(fetchResult is Result.Success)

        val orders = db.orderDao().getByRouteAndDate(routeId = "ROUTE_A", deliveryDate = "2026-01-17")
        assertEquals(15, orders.size)

        orders.forEach { order ->
            assertEquals("ITEMS_PENDING", order.downloadStatus)
            assertEquals(0, order.itemsDownloaded)

            // No existen items finales para pedidos no descargados
            assertEquals(0, db.orderItemDao().getByOrderId(order.orderId).size)
        }

        // WHEN: download items for the very large order
        val downloadResult = downloadItems.execute(orderId = "ORDER_BIG_20")

        // THEN: pedido descargado completo
        assertTrue(downloadResult is Result.Success)

        val downloaded = db.orderDao().getById("ORDER_BIG_20")
        assertTrue(downloaded != null)
        assertEquals("COMPLETED", downloaded!!.downloadStatus)
        assertEquals(20, downloaded.itemsCount)
        assertEquals(downloaded.itemsCount, downloaded.itemsDownloaded)

        // Items finales
        val finalItems = db.orderItemDao().getByOrderId("ORDER_BIG_20")
        assertEquals(20, finalItems.size)
        assertTrue(finalItems.all { it.orderId == "ORDER_BIG_20" })

        // Staging vacío
        val stagingCount = db.orderItemStagingDao().countByOrderId("ORDER_BIG_20")
        assertEquals(0, stagingCount)

        // Total == sum(unitPrice * quantity)
        val expectedTotal = fakeRemote.expectedTotalForOrder("ORDER_BIG_20")
        assertEquals(expectedTotal, downloaded.totalAmount ?: 0.0, 0.0001)

        // Pedidos no descargados: siguen pendientes y sin items
        val notDownloaded = db.orderDao().getByRouteAndDate(routeId = "ROUTE_A", deliveryDate = "2026-01-17")
            .filter { it.orderId != "ORDER_BIG_20" }

        assertEquals(14, notDownloaded.size)
        notDownloaded.forEach { o ->
            assertEquals("ITEMS_PENDING", o.downloadStatus)
            assertEquals(0, o.itemsDownloaded)
            assertEquals(0, db.orderItemDao().getByOrderId(o.orderId).size)
        }
    }

    /**
     * Fake determinista: devuelve 15 pedidos para ROUTE_A + 2026-01-17.
     *
     * Distribución:
     * - 5 pequeños (3)
     * - 5 medianos (8)
     * - 4 grandes (12)
     * - 1 muy grande (20) => ORDER_BIG_20
     */
    private class FakeOrderRemoteDataSource : OrderRemoteDataSource {

        private val routeId = "ROUTE_A"
        private val date = "2026-01-17"

        private val orders: List<OrderRemoteDataSource.OrderHeaderDto> = buildList {
            // 5 small
            repeat(5) { idx ->
                add(
                    OrderRemoteDataSource.OrderHeaderDto(
                        orderId = "ORDER_SMALL_${idx + 1}",
                        routeId = routeId,
                        deliveryDate = date,
                        clientName = "Cliente Small ${idx + 1}",
                        clientAddress = "Dirección Small ${idx + 1}",
                        sellerName = "Seller A",
                        itemsCount = 3,
                    )
                )
            }
            // 5 medium
            repeat(5) { idx ->
                add(
                    OrderRemoteDataSource.OrderHeaderDto(
                        orderId = "ORDER_MED_${idx + 1}",
                        routeId = routeId,
                        deliveryDate = date,
                        clientName = "Cliente Med ${idx + 1}",
                        clientAddress = "Dirección Med ${idx + 1}",
                        sellerName = "Seller A",
                        itemsCount = 8,
                    )
                )
            }
            // 4 big
            repeat(4) { idx ->
                add(
                    OrderRemoteDataSource.OrderHeaderDto(
                        orderId = "ORDER_BIG_${idx + 1}",
                        routeId = routeId,
                        deliveryDate = date,
                        clientName = "Cliente Big ${idx + 1}",
                        clientAddress = "Dirección Big ${idx + 1}",
                        sellerName = "Seller A",
                        itemsCount = 12,
                    )
                )
            }
            // 1 very big
            add(
                OrderRemoteDataSource.OrderHeaderDto(
                    orderId = "ORDER_BIG_20",
                    routeId = routeId,
                    deliveryDate = date,
                    clientName = "Cliente Muy Grande",
                    clientAddress = "Dirección Muy Grande",
                    sellerName = "Seller A",
                    itemsCount = 20,
                )
            )
        }

        private val itemsByOrderId: Map<String, List<OrderRemoteDataSource.OrderItemDto>> =
            orders.associate { header ->
                header.orderId to buildItems(orderId = header.orderId, count = header.itemsCount)
            }

        override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderRemoteDataSource.OrderHeaderDto> {
            return if (routeId == this.routeId && deliveryDate == date) orders else emptyList()
        }

        override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
            if (routeId != this.routeId) return emptyList()
            return itemsByOrderId[orderId].orEmpty()
        }

        fun expectedTotalForOrder(orderId: String): Double {
            val items = itemsByOrderId[orderId].orEmpty()
            return items.sumOf { it.unitPrice * it.quantity }
        }

        private fun buildItems(orderId: String, count: Int): List<OrderRemoteDataSource.OrderItemDto> {
            // Determinista: unitPrice y quantity derivadas del índice.
            // productId y productName únicos por pedido.
            return (1..count).map { i ->
                OrderRemoteDataSource.OrderItemDto(
                    productId = "$orderId-P$i",
                    productName = "Producto $i ($orderId)",
                    unitPrice = 10.0 + i,
                    quantity = 1,
                )
            }
        }
    }
}
