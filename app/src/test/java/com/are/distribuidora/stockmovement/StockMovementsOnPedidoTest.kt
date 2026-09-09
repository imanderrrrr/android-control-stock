package com.are.distribuidora.stockmovement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.pedido.PedidoRemoteDataSource
import com.are.distribuidora.data.repository.PedidoRepositoryImpl
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoItemInput
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.PreviousItemSnapshot
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.model.StockMovementIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 4.1 — Los pedidos mueven el stock a través del LIBRO DE MOVIMIENTOS (pipeline A, "Mis pedidos").
 *
 * Cubre la queja "el inventario no descuenta tampoco suma":
 *  - confirmar → un SALIDA/PEDIDO por ítem + stock local ajustado, todo en una transacción;
 *  - editar    → PEDIDO_EDICION por la diferencia de cada ítem (incluye ítems quitados y nuevos);
 *  - borrar    → ENTRADA/PEDIDO_BORRADO por ítem (el caso que hoy falla: borrar no restauraba);
 *  - ids determinísticos + inserción idempotente (un reintento nunca descuenta dos veces);
 *  - el worker sube los movimientos pendientes CON el pedido y los marca SYNCED;
 *  - un pedido que nunca subió se borra deshaciendo sus movimientos sin ruido en el servidor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StockMovementsOnPedidoTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var remote: RecordingPedidoRemote
    private lateinit var repo: PedidoRepositoryImpl

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = RecordingPedidoRemote()
        repo = PedidoRepositoryImpl(
            database = db,
            pedidoDao = db.pedidoDao(),
            pedidoItemDao = db.pedidoItemDao(),
            productDao = db.productDao(),
            remoteDataSource = remote,
            currentUserIdProvider = FakeCurrentUser(uid = "vend-1", name = "Vendedor Uno"),
            movementDao = db.stockMovementDao(),
        )
        db.productDao().insert(product("A", stock = 50))
        db.productDao().insert(product("B", stock = 20))
    }

    @After
    fun tearDown() = db.close()

    // ───────────────────────────── crear ─────────────────────────────

    @Test
    fun `confirmar pedido genera un SALIDA PEDIDO por item y descuenta el stock local`() = runTest {
        val pedidoId = create(items = listOf("A" to 10, "B" to 3, "custom_x" to 5))

        assertEquals(40, stock("A"))
        assertEquals(17, stock("B"))

        val movements = db.stockMovementDao().getByOrderId(pedidoId)
        assertEquals("ítem personalizado no genera movimiento", 2, movements.size)
        movements.forEach { m ->
            assertEquals(MovementType.SALIDA.name, m.type)
            assertEquals(MovementReason.PEDIDO.name, m.reason)
            assertEquals(SyncStatus.PENDING_CREATE, m.syncStatus)
            assertEquals("vend-1", m.createdBy)
            assertEquals("Vendedor Uno", m.createdByName)
        }
        val items = db.pedidoItemDao().getActiveItemsByPedidoId(pedidoId)
        val itemA = items.first { it.productoId == "A" }
        assertEquals(
            "id determinístico derivado de pedido + ítem + versión",
            StockMovementIds.forOrderItem(pedidoId, itemA.id, 1),
            movements.first { it.productId == "A" }.id,
        )
        assertEquals("el producto NO se ensucia (el stock no se sube)", SyncStatus.SYNCED, db.productDao().getById("A")!!.syncStatus)
    }

    @Test
    fun `el stock puede quedar negativo al confirmar`() = runTest {
        create(items = listOf("B" to 25))
        assertEquals(-5, stock("B"))
    }

    // ───────────────────────────── editar ─────────────────────────────

    @Test
    fun `editar genera PEDIDO_EDICION por la diferencia de cada item`() = runTest {
        val pedidoId = create(items = listOf("A" to 10, "B" to 3))
        val items = db.pedidoItemDao().getActiveItemsByPedidoId(pedidoId)
        val itemA = items.first { it.productoId == "A" }
        val itemB = items.first { it.productoId == "B" }

        // A: 10 → 4 (+6 al stock); B: quitado (+3); nuevo ítem de A: 2 (−2).
        val result = repo.editPedido(
            EditPedidoParams(
                pedidoId = pedidoId,
                vendedorId = "vend-1",
                clienteId = "cli-1",
                itemsToUpsert = listOf(
                    EditPedidoItemInput(itemId = itemA.id, productoId = "A", nombre = "P-A", precioUnitario = 10.0, cantidad = 4, descuentoItem = 0.0),
                    EditPedidoItemInput(itemId = null, productoId = "A", nombre = "P-A", precioUnitario = 10.0, cantidad = 2, descuentoItem = 0.0),
                ),
                itemIdsToDelete = listOf(itemB.id),
                previousItems = listOf(
                    PreviousItemSnapshot(itemA.id, "A", 10),
                    PreviousItemSnapshot(itemB.id, "B", 3),
                ),
                descuentoGlobal = 0.0,
            )
        )
        assertTrue(result is Result.Success)

        assertEquals("50 − 10 + 6 − 2", 44, stock("A"))
        assertEquals("20 − 3 + 3", 20, stock("B"))

        val edits = db.stockMovementDao().getByOrderId(pedidoId).filter { it.reason == MovementReason.PEDIDO_EDICION.name }
        assertEquals(3, edits.size)
        val entradaA = edits.first { it.productId == "A" && it.type == MovementType.ENTRADA.name }
        assertEquals(6, entradaA.quantity)
        assertEquals(StockMovementIds.forOrderItem(pedidoId, itemA.id, 2), entradaA.id)
        val entradaB = edits.first { it.productId == "B" }
        assertEquals(MovementType.ENTRADA.name, entradaB.type)
        assertEquals(3, entradaB.quantity)
        val salidaNuevo = edits.first { it.productId == "A" && it.type == MovementType.SALIDA.name }
        assertEquals(2, salidaNuevo.quantity)
        assertEquals("la versión del pedido se incrementa y persiste", 2, db.pedidoDao().getById(pedidoId)!!.version)
    }

    @Test
    fun `editar sin cambiar cantidades no genera movimientos`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        val itemA = db.pedidoItemDao().getActiveItemsByPedidoId(pedidoId).single()
        repo.editPedido(
            EditPedidoParams(
                pedidoId = pedidoId, vendedorId = "vend-1", clienteId = "cli-1",
                itemsToUpsert = listOf(EditPedidoItemInput(itemA.id, "A", "P-A", 12.0, 10, 0.0)),
                itemIdsToDelete = emptyList(),
                previousItems = listOf(PreviousItemSnapshot(itemA.id, "A", 10)),
                descuentoGlobal = 0.0,
            )
        )
        assertEquals(1, db.stockMovementDao().getByOrderId(pedidoId).size)
        assertEquals(40, stock("A"))
    }

    // ───────────────────────────── borrar ─────────────────────────────

    /** EL BUG: borrar un pedido sincronizado debe devolver el stock. */
    @Test
    fun `borrar un pedido SYNCED genera ENTRADA PEDIDO_BORRADO por item y restaura el stock`() = runTest {
        val pedidoId = create(items = listOf("A" to 10, "B" to 3))
        syncViaWorker(pedidoId)
        assertEquals(40, stock("A"))

        val result = repo.deletePedido(pedidoId)
        assertTrue(result is Result.Success)

        assertEquals("EL STOCK NO SE RESTAURÓ AL BORRAR", 50, stock("A"))
        assertEquals(20, stock("B"))

        val deletions = db.stockMovementDao().getByOrderId(pedidoId).filter { it.reason == MovementReason.PEDIDO_BORRADO.name }
        assertEquals(2, deletions.size)
        deletions.forEach {
            assertEquals(MovementType.ENTRADA.name, it.type)
            assertEquals("ya están en el servidor (fueron en la transacción del soft delete)", SyncStatus.SYNCED, it.syncStatus)
        }
        assertEquals("los movimientos compensatorios viajaron con el soft delete", 2, remote.lastSoftDeleteMovements.size)
        assertEquals(SyncStatus.PENDING_DELETE, db.pedidoDao().getById(pedidoId)!!.syncStatus)
    }

    @Test
    fun `borrar un pedido que nunca subio deshace sus movimientos localmente sin compensar`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        assertEquals(40, stock("A"))

        val result = repo.deletePedido(pedidoId)
        assertTrue(result is Result.Success)

        assertEquals(50, stock("A"))
        assertTrue("nada quedó en el libro: nada llegó nunca al servidor", db.stockMovementDao().getByOrderId(pedidoId).isEmpty())
        assertNull(db.pedidoDao().getById(pedidoId))
        assertEquals(0, remote.uploads)
    }

    @Test
    fun `si el soft delete remoto falla Room queda intacto`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        syncViaWorker(pedidoId)
        remote.failNext = true

        val result = repo.deletePedido(pedidoId)
        assertTrue(result is Result.Error)

        assertEquals(40, stock("A"))
        assertEquals(SyncStatus.SYNCED, db.pedidoDao().getById(pedidoId)!!.syncStatus)
        assertEquals(1, db.stockMovementDao().getByOrderId(pedidoId).size)
    }

    // ───────────────────────────── idempotencia ─────────────────────────────

    @Test
    fun `ids determinísticos - estables y distintos por versión`() {
        assertEquals(StockMovementIds.forOrderItem("o1", "i1", 1), StockMovementIds.forOrderItem("o1", "i1", 1))
        assertNotEquals(StockMovementIds.forOrderItem("o1", "i1", 1), StockMovementIds.forOrderItem("o1", "i1", 2))
        assertNotEquals(StockMovementIds.forOrderItem("o1", "i1", 1), StockMovementIds.forOrderItem("o1", "i2", 1))
        assertNotEquals(StockMovementIds.forOrderItem("o1", "i1", 1), StockMovementIds.forOrderItemDeletion("o1", "i1"))
        assertNotEquals(StockMovementIds.forOrderItem("o1", "i1", 1), StockMovementIds.forOtherOrderEdit("o1", "i1", 1))
    }

    @Test
    fun `insertar dos veces el mismo movimiento no duplica ni descuenta dos veces`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        val m = db.stockMovementDao().getByOrderId(pedidoId).single()

        val inserted = db.stockMovementDao().insert(m)
        assertEquals("IGNORE → no se inserta", -1L, inserted)
        assertEquals(1, db.stockMovementDao().getByOrderId(pedidoId).size)
        assertEquals(40, stock("A"))
    }

    // ───────────────────────────── subida con el pedido ─────────────────────────────

    @Test
    fun `el worker sube los movimientos con el pedido y los marca SYNCED`() = runTest {
        val pedidoId = create(items = listOf("A" to 10, "B" to 3))

        syncViaWorker(pedidoId)

        assertEquals(2, remote.lastUploadMovements.size)
        assertTrue(remote.lastUploadMovements.all { it.orderId == pedidoId && it.type == MovementType.SALIDA })
        db.stockMovementDao().getByOrderId(pedidoId).forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
        assertEquals(SyncStatus.SYNCED, db.pedidoDao().getById(pedidoId)!!.syncStatus)
        assertEquals(0, db.stockMovementDao().sumPendingDelta("A"))
    }

    @Test
    fun `si la subida falla los movimientos vuelven a pendiente`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        remote.failNext = true

        val pending = repo.getPendingPedidosForSync(10).single()
        try { repo.uploadAndMarkSynced(pending) } catch (_: Exception) {}

        db.stockMovementDao().getByOrderId(pedidoId).forEach { assertEquals(SyncStatus.PENDING_CREATE, it.syncStatus) }
        assertEquals(SyncStatus.PENDING_CREATE, db.pedidoDao().getById(pedidoId)!!.syncStatus)
    }

    @Test
    fun `una edicion despues de subir viaja solo con sus movimientos nuevos`() = runTest {
        val pedidoId = create(items = listOf("A" to 10))
        syncViaWorker(pedidoId)
        val itemA = db.pedidoItemDao().getActiveItemsByPedidoId(pedidoId).single()
        repo.editPedido(
            EditPedidoParams(
                pedidoId = pedidoId, vendedorId = "vend-1", clienteId = "cli-1",
                itemsToUpsert = listOf(EditPedidoItemInput(itemA.id, "A", "P-A", 10.0, 15, 0.0)),
                itemIdsToDelete = emptyList(),
                previousItems = listOf(PreviousItemSnapshot(itemA.id, "A", 10)),
                descuentoGlobal = 0.0,
            )
        )
        assertEquals(35, stock("A"))

        val pendingUpdate = repo.getPendingUpdatePedidosForSync(10).single()
        repo.updateAndMarkSynced(pendingUpdate)

        assertEquals(1, remote.lastUploadMovements.size)
        val m = remote.lastUploadMovements.single()
        assertEquals(MovementReason.PEDIDO_EDICION, m.reason)
        assertEquals(MovementType.SALIDA, m.type)
        assertEquals(5, m.quantity)
        assertEquals(2, remote.lastUploadPayload!!.version)
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private suspend fun create(items: List<Pair<String, Int>>): String {
        val result = repo.createPedido(
            CreatePedidoParams(
                vendedorId = "vend-1",
                routeId = "route-1",
                deliveryDate = "2026-09-08",
                clienteId = "cli-1",
                clienteSnapshot = ClienteSnapshot(nombre = "Tienda", telefono = null, direccion = null),
                items = items.map { (pid, qty) -> CreatePedidoItemInput(pid, "P-$pid", 10.0, qty, 0.0) },
                descuentoGlobal = 0.0,
                totalRedondeado = items.sumOf { it.second * 10.0 },
            )
        )
        return (result as Result.Success).value
    }

    private suspend fun syncViaWorker(pedidoId: String) {
        val pending = repo.getPendingPedidosForSync(10).first { it.pedido.id == pedidoId }
        repo.uploadAndMarkSynced(pending)
    }

    private suspend fun stock(id: String): Int = db.productDao().getById(id)!!.stock

    private fun product(id: String, stock: Int) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null, stock = stock,
        isActive = true, isDeleted = false, syncStatus = SyncStatus.SYNCED,
        createdAt = 1L, updatedAt = 1L, lastSyncedAt = 1L,
    )

    /** Remoto que registra lo que recibe; puede fallar una vez. */
    private class RecordingPedidoRemote : PedidoRemoteDataSource {
        var uploads = 0
        var failNext = false
        var lastUploadMovements: List<StockMovement> = emptyList()
        var lastUploadPayload: PedidoRemoteDataSource.PedidoPayload? = null
        var lastSoftDeleteMovements: List<StockMovement> = emptyList()

        private fun maybeFail() {
            if (failNext) { failNext = false; throw java.io.IOException("red caída") }
        }

        override suspend fun uploadPedido(
            pedidoId: String,
            payload: PedidoRemoteDataSource.PedidoPayload,
            items: List<PedidoRemoteDataSource.PedidoItemPayload>,
            movements: List<StockMovement>,
        ) {
            maybeFail()
            uploads++
            lastUploadPayload = payload
            lastUploadMovements = movements
        }

        override suspend fun softDeletePedido(
            routeId: String, pedidoId: String, orderKey: String?, deletedByUid: String?, movements: List<StockMovement>,
        ) {
            maybeFail()
            lastSoftDeleteMovements = movements
        }

        override suspend fun markPedidoExpiredInCloud(routeId: String, pedidoId: String) {}
        override suspend fun hardDeletePedidoFromCloud(routeId: String, pedidoId: String) {}
    }
}
