package com.are.distribuidora.stockmovement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.orders.data.local.RoomOrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.model.StockMovementIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 4.1 — Pipeline B ("Otros pedidos": pedidos AJENOS editados/borrados por un admin) también mueve el
 * stock por el libro de movimientos: PEDIDO_EDICION por diferencia, PEDIDO_BORRADO al borrar, ids
 * determinísticos con `editVersion`, y los movimientos viajan con `uploadOrderEdit` / `markOrderDeleted`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OtrosOrderEditMovementsTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var remote: RecordingOrderRemote
    private lateinit var repo: OfflineFirstOrderRepository

    private val orderId = "o-ajeno-1"
    private val routeId = "r1"

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java).allowMainThreadQueries().build()
        remote = RecordingOrderRemote()
        val local = RoomOrderLocalDataSource(
            db = db,
            orderDao = db.orderDao(),
            orderItemDao = db.orderItemDao(),
            stagingDao = db.orderItemStagingDao(),
            movementDao = db.stockMovementDao(),
            productDao = db.productDao(),
        )
        repo = OfflineFirstOrderRepository(local = local, remote = remote, currentUserIdProvider = FakeCurrentUser("admin-1", "Yonatan"))

        db.productDao().insert(product("A", 50))
        db.productDao().insert(product("B", 20))
        // Pedido ajeno ya descargado con sus ítems (creado por otro vendedor; su stock ya se descontó en SU teléfono).
        db.orderDao().upsert(
            OrderEntity(
                orderId = orderId, routeId = routeId, deliveryDate = "2026-09-08", clientName = "Tienda",
                clientAddress = null, sellerName = "Vendedor A", itemsCount = 2, itemsDownloaded = 2, totalAmount = 130.0,
                downloadStatus = "COMPLETED", failedReasonCode = null, failedReasonMessage = null, failedAttempts = 0,
                lastAttemptAt = null, createdAt = 1L, updatedAt = 1L, vendedorId = "vend-A", isDeleted = false,
            )
        )
        db.orderItemDao().insertAll(
            listOf(
                OrderItemEntity(itemId = "iA", orderId = orderId, productId = "A", productName = "P-A", unitPrice = 10.0, quantity = 10, createdAt = 1L),
                OrderItemEntity(itemId = "iB", orderId = orderId, productId = "B", productName = "P-B", unitPrice = 10.0, quantity = 3, createdAt = 1L),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `editar un pedido ajeno genera PEDIDO_EDICION por diferencia y ajusta el stock local`() = runTest {
        // A: 10 → 4 (+6), B quitado (+3), C nuevo 2 (−2, producto no local → igual se registra), custom ignorado.
        db.productDao().insert(product("C", 5))
        val result = repo.editOrderItems(
            orderId,
            listOf(
                EditOrderItemInput(itemId = "iA", productId = "A", productName = "P-A", unitPrice = 10.0, quantity = 4),
                EditOrderItemInput(itemId = "iC", productId = "C", productName = "P-C", unitPrice = 10.0, quantity = 2),
                EditOrderItemInput(itemId = "iX", productId = "custom_1", productName = "Personalizado", unitPrice = 1.0, quantity = 9),
            ),
        )
        assertTrue(result is Result.Success)

        assertEquals(56, stock("A"))
        assertEquals(23, stock("B"))
        assertEquals(3, stock("C"))

        val movements = db.stockMovementDao().getByOrderId(orderId)
        assertEquals(3, movements.size)
        movements.forEach {
            assertEquals(MovementReason.PEDIDO_EDICION.name, it.reason)
            assertEquals(SyncStatus.PENDING_CREATE, it.syncStatus)
            assertEquals("admin-1", it.createdBy)
        }
        assertEquals(StockMovementIds.forOtherOrderEdit(orderId, "iA", 1), movements.first { it.productId == "A" }.id)
        assertEquals(1, db.orderDao().getById(orderId)!!.editVersion)
        assertTrue(db.orderDao().getById(orderId)!!.pendingUpload)
    }

    @Test
    fun `una segunda edicion usa editVersion 2 y no colisiona con la primera`() = runTest {
        repo.editOrderItems(orderId, listOf(EditOrderItemInput("iA", "A", "P-A", 10.0, 4), EditOrderItemInput("iB", "B", "P-B", 10.0, 3)))
        repo.editOrderItems(orderId, listOf(EditOrderItemInput("iA", "A", "P-A", 10.0, 7), EditOrderItemInput("iB", "B", "P-B", 10.0, 3)))

        val ids = db.stockMovementDao().getByOrderId(orderId).map { it.id }
        assertTrue(ids.contains(StockMovementIds.forOtherOrderEdit(orderId, "iA", 1)))
        assertTrue(ids.contains(StockMovementIds.forOtherOrderEdit(orderId, "iA", 2)))
        assertEquals("50 + 6 − 3", 53, stock("A"))
        assertEquals(2, db.orderDao().getById(orderId)!!.editVersion)
    }

    @Test
    fun `el worker sube la edicion con sus movimientos y los marca SYNCED`() = runTest {
        repo.editOrderItems(orderId, listOf(EditOrderItemInput("iA", "A", "P-A", 10.0, 4), EditOrderItemInput("iB", "B", "P-B", 10.0, 3)))

        val result = repo.uploadPendingOrders()
        assertTrue(result is Result.Success)

        assertEquals(1, remote.lastEditMovements.size)
        assertEquals(6, remote.lastEditMovements.single().quantity)
        assertEquals(MovementType.ENTRADA, remote.lastEditMovements.single().type)
        db.stockMovementDao().getByOrderId(orderId).forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
        assertTrue(!db.orderDao().getById(orderId)!!.pendingUpload)
    }

    @Test
    fun `si la subida falla los movimientos vuelven a pendiente y el flag se conserva`() = runTest {
        repo.editOrderItems(orderId, listOf(EditOrderItemInput("iA", "A", "P-A", 10.0, 4), EditOrderItemInput("iB", "B", "P-B", 10.0, 3)))
        remote.failNext = true

        val result = repo.uploadPendingOrders()
        assertTrue(result is Result.Error)

        db.stockMovementDao().getByOrderId(orderId).forEach { assertEquals(SyncStatus.PENDING_CREATE, it.syncStatus) }
        assertTrue(db.orderDao().getById(orderId)!!.pendingUpload)
    }

    @Test
    fun `borrar un pedido ajeno genera PEDIDO_BORRADO por item y devuelve el stock`() = runTest {
        val result = repo.deleteOrder(routeId, orderId)
        assertTrue(result is Result.Success)

        assertEquals(60, stock("A"))
        assertEquals(23, stock("B"))
        assertEquals(2, remote.lastDeleteMovements.size)
        remote.lastDeleteMovements.forEach {
            assertEquals(MovementReason.PEDIDO_BORRADO, it.reason)
            assertEquals(MovementType.ENTRADA, it.type)
        }
        db.stockMovementDao().getByOrderId(orderId).forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
        assertTrue(db.orderDao().getById(orderId)!!.isDeleted)
    }

    @Test
    fun `si el borrado remoto falla no se toca el stock local`() = runTest {
        remote.failNext = true
        val result = repo.deleteOrder(routeId, orderId)
        assertTrue(result is Result.Error)
        assertEquals(50, stock("A"))
        assertTrue(db.stockMovementDao().getByOrderId(orderId).isEmpty())
    }

    private suspend fun stock(id: String) = db.productDao().getById(id)!!.stock

    private fun product(id: String, stock: Int) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0, imageUrl = null, imageLocalUri = null,
        barcode = null, stock = stock, isActive = true, isDeleted = false, syncStatus = SyncStatus.SYNCED,
        createdAt = 1L, updatedAt = 1L, lastSyncedAt = 1L,
    )

    private class RecordingOrderRemote : OrderRemoteDataSource {
        var failNext = false
        var lastEditMovements: List<StockMovement> = emptyList()
        var lastDeleteMovements: List<StockMovement> = emptyList()
        private fun maybeFail() { if (failNext) { failNext = false; throw java.io.IOException("sin red") } }

        override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
        override suspend fun fetchAllOrderHeaders(routeId: String) = emptyList<OrderRemoteDataSource.OrderHeaderDto>()
        override suspend fun fetchOrderItems(routeId: String, orderId: String) = emptyList<OrderRemoteDataSource.OrderItemDto>()
        override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) = markOrderDeleted(routeId, orderId, deletedByUid, emptyList())
        override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?, movements: List<StockMovement>) {
            maybeFail(); lastDeleteMovements = movements
        }
        override suspend fun uploadOrderEdit(routeId: String, orderId: String, items: List<OrderRemoteDataSource.OrderItemDto>, totalAmount: Double, editedByUid: String?) =
            uploadOrderEdit(routeId, orderId, items, totalAmount, editedByUid, emptyList())
        override suspend fun uploadOrderEdit(routeId: String, orderId: String, items: List<OrderRemoteDataSource.OrderItemDto>, totalAmount: Double, editedByUid: String?, movements: List<StockMovement>) {
            maybeFail(); lastEditMovements = movements
        }
    }
}
