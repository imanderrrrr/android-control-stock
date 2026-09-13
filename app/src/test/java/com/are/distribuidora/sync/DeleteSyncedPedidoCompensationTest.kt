package com.are.distribuidora.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore
import com.are.distribuidora.data.repository.PedidoRepositoryImpl
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.stockmovement.FakeCurrentUser
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovementIds
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PENDIENTE 2 de la integración de release/4.1 — borrar un pedido que YA está en Firestore debe
 * **COMPENSAR** con movimientos ENTRADA/PEDIDO_BORRADO, nunca borrar físicamente los movimientos
 * (ese camino, `deleteUnsyncedByOrderId`, es exclusivo del pedido que nunca llegó al servidor).
 *
 * Solo se había probado el caso PENDING_CREATE. Aquí se fija el camino SYNCED extremo a extremo
 * contra el [FakeFirestoreBackend] compartido: el libro de movimientos queda completo y auditable,
 * el contador remoto vuelve a su valor y un segundo teléfono converge al bajar el catálogo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeleteSyncedPedidoCompensationTest {

    private lateinit var backend: FakeFirestoreBackend
    private lateinit var db: DistribuidoraDatabase
    private lateinit var repo: PedidoRepositoryImpl
    private lateinit var otherDeviceDb: DistribuidoraDatabase
    private lateinit var otherDeviceSync: ProductSyncRepositoryImpl

    @Before
    fun setup() = runTest {
        backend = FakeFirestoreBackend()
        backend.seedProduct("A", stock = 50)
        // Producto ya en 0, como el 88% del catálogo de producción: si la compensación no ocurre,
        // el error se esconde detrás de un contador colapsado.
        backend.seedProduct("Z", stock = 0)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PedidoRepositoryImpl(
            database = db,
            pedidoDao = db.pedidoDao(),
            pedidoItemDao = db.pedidoItemDao(),
            productDao = db.productDao(),
            remoteDataSource = backend.pedidoRemote(),
            currentUserIdProvider = FakeCurrentUser(uid = "vend-1", name = "Vendedor Uno"),
            movementDao = db.stockMovementDao(),
        )
        seed(db, "A", 50)
        seed(db, "Z", 0)

        otherDeviceDb = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries().build()
        otherDeviceSync = ProductSyncRepositoryImpl(
            remote = backend.catalogRemote(),
            local = otherDeviceDb.productDao(),
            database = otherDeviceDb,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = otherDeviceDb.pendingUploadDao(),
            movementDao = otherDeviceDb.stockMovementDao(),
            cursorStore = InMemoryProductSyncCursorStore(),
        )
        seed(otherDeviceDb, "A", 50)
        seed(otherDeviceDb, "Z", 0)
    }

    @After
    fun tearDown() {
        db.close()
        otherDeviceDb.close()
    }

    @Test
    fun `borrar un pedido SYNCED compensa y NO borra los movimientos originales`() = runTest {
        val pedidoId = createAndUpload("A" to 10, "Z" to 4)
        assertEquals(40, stock("A"))
        assertEquals(-4, stock("Z"))
        assertEquals(40, backend.stockOf("A"))
        assertEquals(-4, backend.stockOf("Z"))

        val result = repo.deletePedido(pedidoId)
        assertTrue(result is Result.Success)

        // Local: contador restaurado y libro COMPLETO (salidas originales + entradas compensatorias).
        assertEquals(50, stock("A"))
        assertEquals(0, stock("Z"))
        val book = db.stockMovementDao().getByOrderId(pedidoId)
        assertEquals("el libro conserva las 2 SALIDA y suma 2 ENTRADA", 4, book.size)
        assertEquals(2, book.count { it.type == MovementType.SALIDA.name && it.reason == MovementReason.PEDIDO.name })
        val compensations = book.filter { it.reason == MovementReason.PEDIDO_BORRADO.name }
        assertEquals(2, compensations.size)
        compensations.forEach {
            assertEquals(MovementType.ENTRADA.name, it.type)
            assertEquals("viajaron dentro de la transacción del soft delete", SyncStatus.SYNCED, it.syncStatus)
            assertTrue(it.id.endsWith("_del"))
        }
        assertEquals(0, db.stockMovementDao().countPending())

        // Remoto: el contador vuelve a su valor y el pedido queda como tombstone (soft delete).
        assertEquals(50, backend.stockOf("A"))
        assertEquals(0, backend.stockOf("Z"))
        assertTrue(pedidoId in backend.deletedPedidos)
        assertEquals(SyncStatus.PENDING_DELETE, db.pedidoDao().getById(pedidoId)!!.syncStatus)

        // El otro teléfono converge al bajar el catálogo.
        otherDeviceSync.syncDownstream()
        assertEquals(50, otherDeviceDb.productDao().getById("A")!!.stock)
        assertEquals(0, otherDeviceDb.productDao().getById("Z")!!.stock)
    }

    /** Los ids compensatorios son determinísticos: reintentar el borrado no devuelve dos veces. */
    @Test
    fun `reintentar el borrado no devuelve el stock dos veces`() = runTest {
        val pedidoId = createAndUpload("A" to 10)
        val itemId = db.pedidoItemDao().getActiveItemsByPedidoId(pedidoId).single().id

        repo.deletePedido(pedidoId)
        assertEquals(50, backend.stockOf("A"))
        assertEquals(
            StockMovementIds.forOrderItemDeletion(pedidoId, itemId),
            db.stockMovementDao().getByOrderId(pedidoId).single { it.reason == MovementReason.PEDIDO_BORRADO.name }.id,
        )

        // El mismo movimiento compensatorio vuelve a llegar al servidor (reintento del worker).
        backend.applyMovements(
            db.stockMovementDao().getByOrderId(pedidoId)
                .filter { it.reason == MovementReason.PEDIDO_BORRADO.name }
                .map { it.toDomain() }
        )
        assertEquals("create-only: la segunda escritura es no-op", 50, backend.stockOf("A"))
    }

    /**
     * Contraste con el otro camino: un pedido que NUNCA llegó a Firestore sí borra físicamente sus
     * movimientos, porque no hay nada que compensar en el servidor.
     */
    @Test
    fun `un pedido nunca subido borra sus movimientos en vez de compensar`() = runTest {
        val pedidoId = create("A" to 10)
        assertEquals(40, stock("A"))

        assertTrue(repo.deletePedido(pedidoId) is Result.Success)

        assertEquals(50, stock("A"))
        assertTrue(db.stockMovementDao().getByOrderId(pedidoId).isEmpty())
        assertNull(db.pedidoDao().getById(pedidoId))
        assertEquals("nada llegó al servidor", 50, backend.stockOf("A"))
        assertTrue(pedidoId !in backend.uploadedPedidos)
        assertTrue(pedidoId !in backend.deletedPedidos)
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private suspend fun create(vararg items: Pair<String, Int>): String {
        val result = repo.createPedido(
            CreatePedidoParams(
                vendedorId = "vend-1",
                routeId = "route-1",
                deliveryDate = "2026-09-13",
                clienteId = "cli-1",
                clienteSnapshot = ClienteSnapshot(nombre = "Tienda", telefono = null, direccion = null),
                items = items.map { (pid, qty) -> CreatePedidoItemInput(pid, "P-$pid", 10.0, qty, 0.0) },
                descuentoGlobal = 0.0,
                totalRedondeado = items.sumOf { it.second * 10.0 },
            )
        )
        return (result as Result.Success).value
    }

    private suspend fun createAndUpload(vararg items: Pair<String, Int>): String {
        val pedidoId = create(*items)
        val pending = repo.getPendingPedidosForSync(10).first { it.pedido.id == pedidoId }
        repo.uploadAndMarkSynced(pending)
        return pedidoId
    }

    private suspend fun stock(id: String): Int = db.productDao().getById(id)!!.stock

    private suspend fun seed(target: DistribuidoraDatabase, id: String, stock: Int) {
        target.productDao().insert(
            ProductEntity(
                id = id, name = "P-$id", description = null, category = null, price = 10.0,
                imageUrl = null, imageLocalUri = null, barcode = null, stock = stock,
                isActive = true, isDeleted = false, syncStatus = SyncStatus.SYNCED,
                createdAt = 1L, updatedAt = 1L, lastSyncedAt = 1L,
            )
        )
    }
}
