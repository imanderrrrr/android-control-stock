package com.are.distribuidora.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.model.RemoteProduct
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.product.SyncProductsUseCase
import com.are.distribuidora.stockmovement.data.local.dao.StockMovementDao
import com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * 4.1 — Stock movido por el LIBRO DE MOVIMIENTOS, no por escritura absoluta del contador.
 *
 *  - [ProductDao.applyMovement] suma/resta con signo, permite negativo y NO ensucia el producto
 *    (ni syncStatus ni updatedAt): el teléfono ya no sube `stock`.
 *  - El downsync reconstruye `stock local = stock remoto + movimientos locales pendientes`, así una
 *    venta sin subir no "desaparece" cuando baja el contador viejo, y converge cuando el movimiento
 *    llega al servidor (que aplicó su increment).
 *  - La subida de productos OMITE `stock` y, al marcar SYNCED, adopta el `updatedAt` del servidor
 *    (cursor de bajada sano) y su stock vigente.
 *  - markSynced conserva el guard `AND syncStatus = 'SYNCING'` (anti lost-update).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductOrderStockSyncTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
    private lateinit var movementDao: StockMovementDao
    private lateinit var remote: FakeRemote
    private lateinit var syncRepo: ProductSyncRepositoryImpl
    private lateinit var useCase: SyncProductsUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
        movementDao = db.stockMovementDao()
        remote = FakeRemote()
        syncRepo = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
            movementDao = movementDao,
            cursorStore = com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore(),
        )
        useCase = SyncProductsUseCase(
            repository = syncRepo,
            connectivityChecker = FakeConnectivityChecker(true),
            logger = FakeLogger(),
        )
    }

    @After
    fun tearDown() = db.close()

    // ───────────────────────────── applyMovement ─────────────────────────────

    @Test
    fun `applyMovement resta y suma sin tocar syncStatus ni updatedAt`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, updatedAt = 1000L))

        dao.applyMovement("p1", -10)
        var row = dao.getById("p1")!!
        assertEquals(40, row.stock)
        assertEquals("mover stock ya no ensucia el producto (no se sube stock)", SyncStatus.SYNCED, row.syncStatus)
        assertEquals("updatedAt es del servidor; no se toca", 1000L, row.updatedAt)

        dao.applyMovement("p1", +25)
        row = dao.getById("p1")!!
        assertEquals(65, row.stock)
    }

    @Test
    fun `applyMovement permite stock negativo`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 2, updatedAt = 1000L))
        dao.applyMovement("p1", -5)
        assertEquals(-3, dao.getById("p1")!!.stock)
    }

    @Test
    fun `applyMovement preserva PENDING_CREATE y PENDING_UPDATE`() = runTest {
        dao.insert(dirtyEntity(id = "c", stock = 5, updatedAt = 0L, status = SyncStatus.PENDING_CREATE))
        dao.insert(dirtyEntity(id = "u", stock = 5, updatedAt = 1L, status = SyncStatus.PENDING_UPDATE))
        dao.applyMovement("c", -1)
        dao.applyMovement("u", -1)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById("c")!!.syncStatus)
        assertEquals(SyncStatus.PENDING_UPDATE, dao.getById("u")!!.syncStatus)
    }

    // ───────────────────────────── pendientes vs downsync ─────────────────────────────

    /** THE order-stock bug (4.1 flavor): a not-yet-uploaded movement must survive a downsync. */
    @Test
    fun `movimiento pendiente sobrevive al downsync - stock local = remoto + pendientes`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, updatedRemoteAt = 1000L)

        // Lo que hace createPedido: movimiento SALIDA 10 pendiente + stock local 50 → 40.
        movementDao.insert(movement(id = "m1", productId = "p1", type = "SALIDA", qty = 10, status = SyncStatus.PENDING_CREATE))
        dao.applyMovement("p1", -10)
        assertEquals(40, dao.getById("p1")!!.stock)

        // El remoto todavía dice 50 (el movimiento no ha subido). Antes esto revertía la venta.
        syncRepo.syncDownstream()
        assertEquals("STOCK FROM THE ORDER WAS SILENTLY REVERTED BY DOWNSYNC", 40, dao.getById("p1")!!.stock)

        // Un cambio remoto legítimo (otro teléfono vendió 5 → remoto 45) también se refleja: 45 - 10.
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 45, updatedRemoteAt = 2000L)
        syncRepo.syncDownstream()
        assertEquals(35, dao.getById("p1")!!.stock)
    }

    @Test
    fun `cuando el movimiento sube el remoto ya lo incluye y el local converge sin doble descuento`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, updatedRemoteAt = 1000L)
        movementDao.insert(movement(id = "m1", productId = "p1", type = "SALIDA", qty = 10, status = SyncStatus.PENDING_CREATE))
        dao.applyMovement("p1", -10)

        // El worker de pedidos sube el movimiento: el servidor aplica increment(-10) y lo marca SYNCED.
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 40, updatedRemoteAt = 3000L)
        movementDao.markSynced(listOf("m1"), at = 3000L)

        repeat(3) { syncRepo.syncDownstream() }
        val row = dao.getById("p1")!!
        assertEquals("converge al valor del servidor, sin descontar dos veces", 40, row.stock)
        assertEquals(3000L, row.updatedAt)
    }

    @Test
    fun `remoto sin campo stock conserva el stock local`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = null, updatedRemoteAt = 2000L, name = "Renombrado")
        syncRepo.syncDownstream()
        val row = dao.getById("p1")!!
        assertEquals(50, row.stock)
        assertEquals("Renombrado", row.name)
    }

    // ───────────────────────────── subida sin stock + updatedAt del servidor ─────────────────────────────

    @Test
    fun `la subida de productos NO envia stock y adopta el updatedAt y el stock del servidor`() = runTest {
        // Edición local de precio (PENDING_UPDATE) con stock local 40; el servidor tiene stock 37
        // (otro teléfono vendió mientras estábamos sucios) y un updatedAt más nuevo tras subir.
        dao.insert(dirtyEntity(id = "p1", stock = 40, updatedAt = 1000L, status = SyncStatus.PENDING_UPDATE, price = 12.0))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 37, updatedRemoteAt = 1000L)
        remote.assignServerTimestampOnUpload = 5000L

        assertTrue(useCase().isSuccess)

        assertNull("el teléfono nunca sube stock como valor absoluto", remote.lastUploaded!!.stock)
        assertEquals("el remoto conserva su propio stock", 37, remote.storage["p1"]!!.stock)
        assertEquals("el precio sí sube", 12.0, remote.storage["p1"]!!.price!!, 0.0)

        val row = dao.getById("p1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals("adopta el updatedAt del servidor (cursor sano)", 5000L, row.updatedAt)
        assertEquals("adopta el stock vigente del servidor (+0 pendientes)", 37, row.stock)
    }

    @Test
    fun `al marcar SYNCED el stock adoptado incluye los movimientos aun pendientes`() = runTest {
        dao.insert(dirtyEntity(id = "p1", stock = 30, updatedAt = 1000L, status = SyncStatus.PENDING_UPDATE))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 37, updatedRemoteAt = 1000L)
        movementDao.insert(movement(id = "m1", productId = "p1", type = "SALIDA", qty = 7, status = SyncStatus.PENDING_CREATE))
        remote.assignServerTimestampOnUpload = 5000L

        assertTrue(useCase().isSuccess)

        assertEquals(37 - 7, dao.getById("p1")!!.stock)
    }

    // ───────────────────────────── A2: guard SYNCING ─────────────────────────────

    @Test
    fun `markSynced does not clobber a concurrent edit (SYNCING guard)`() = runTest {
        dao.insert(dirtyEntity(id = "p1", stock = 50, updatedAt = 1000L, status = SyncStatus.SYNCING))
        dao.update(dao.getById("p1")!!.copy(syncStatus = SyncStatus.PENDING_UPDATE, price = 99.0))

        dao.markSynced("p1", lastSyncedAt = 2000L, serverUpdatedAt = 9999L, stock = 1)

        val after = dao.getById("p1")!!
        assertEquals(SyncStatus.PENDING_UPDATE, after.syncStatus)
        assertEquals(99.0, after.price, 0.0)
        assertEquals("updatedAt tampoco se pisa", 1000L, after.updatedAt)
        assertEquals(50, after.stock)
    }

    @Test
    fun `markSynced transitions SYNCING to SYNCED normally and adopts server fields`() = runTest {
        dao.insert(dirtyEntity(id = "p2", stock = 50, updatedAt = 1000L, status = SyncStatus.SYNCING))

        dao.markSynced("p2", lastSyncedAt = 2000L, serverUpdatedAt = 2500L, stock = 48)

        val row = dao.getById("p2")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(2500L, row.updatedAt)
        assertEquals(48, row.stock)
    }

    @Test
    fun `markSynced con nulls conserva updatedAt y stock locales`() = runTest {
        dao.insert(dirtyEntity(id = "p3", stock = 50, updatedAt = 1000L, status = SyncStatus.SYNCING))
        dao.markSynced("p3", lastSyncedAt = 2000L)
        val row = dao.getById("p3")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals(1000L, row.updatedAt)
        assertEquals(50, row.stock)
    }

    // ───────────────────────────── helpers & fakes ─────────────────────────────

    private fun syncedEntity(id: String, stock: Int, updatedAt: Long) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, isActive = true, isDeleted = false,
        syncStatus = SyncStatus.SYNCED, createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun dirtyEntity(id: String, stock: Int, updatedAt: Long, status: SyncStatus, price: Double = 10.0) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = price,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, isActive = true, isDeleted = false,
        syncStatus = status, createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun remoteProduct(id: String, stock: Int?, updatedRemoteAt: Long, name: String = "P-$id") = RemoteProduct(
        id = id, name = name, description = null, category = null, price = 10.0,
        imageUrl = null, barcode = null, stock = stock,
        isActive = true, isDeleted = false, createdRemoteAt = updatedRemoteAt, updatedRemoteAt = updatedRemoteAt,
    )

    private fun movement(id: String, productId: String, type: String, qty: Int, status: SyncStatus) = StockMovementEntity(
        id = id, productId = productId, productName = "P-$productId", type = type, quantity = qty,
        reason = "PEDIDO", orderId = "order-1", note = null, createdBy = "u", createdByName = "U",
        createdAt = 1L, syncStatus = status,
    )

    /**
     * Mimics Firestore: the server assigns updatedAt on upload, NEVER takes `stock` from the payload
     * (the client omits it) and exposes a getById snapshot.
     */
    private class FakeRemote : ProductRemoteDataSource {
        val storage = mutableMapOf<String, RemoteProduct>()
        var assignServerTimestampOnUpload: Long? = null
        var lastUploaded: RemoteProduct? = null

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val list = storage.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (list.isNotEmpty()) emit(list)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            lastUploaded = product
            val serverTs = assignServerTimestampOnUpload ?: product.updatedRemoteAt
            val existingStock = storage[product.id]?.stock
            // set + merge sin stock: el servidor conserva el suyo.
            storage[product.id] = product.copy(stock = product.stock ?: existingStock, updatedRemoteAt = serverTs)
        }

        override suspend fun fetchProductById(id: String): RemoteProduct? = storage[id]

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            storage[id]?.let { storage[id] = it.copy(isDeleted = true, updatedRemoteAt = timestamp) }
        }
    }

    private class FakeConnectivityChecker(private val online: Boolean) : ConnectivityChecker {
        override suspend fun isOnline(): Boolean = online
    }

    private class FakeLogger : Logger {
        override fun d(tag: String, message: String) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }
}
