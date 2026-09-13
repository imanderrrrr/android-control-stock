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
import com.are.distribuidora.data.repository.ProductRepositoryImpl
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.SyncProductsUseCase
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

/**
 * Regression suite for the product-sync DATA-LOSS bug: a downsync silently reverting
 * a local edit (and zeroing stock).
 *
 * The decisive proof is [downsync does NOT revert a PENDING_UPDATE stock edit on an equal-timestamp tie]:
 * it FAILS on the pre-fix code (stock reverts 100 -> 50) and PASSES after the fix.
 *
 * Acceptance criteria covered:
 *  A — a not-yet-durably-synced local row is never overwritten by remote (PENDING_UPDATE tie,
 *      SYNCING, CONFLICT).
 *  B — stock local = remoto + movimientos pendientes (4.1).
 *  C — stock is never zeroed because the remote doc omits the field.
 *  D — after the edit uploads, the app converges (no revert loop, no stuck PENDING).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductSyncDataLossTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
    private lateinit var remote: ControllableFakeRemote
    private lateinit var syncRepo: ProductSyncRepositoryImpl
    private lateinit var productRepo: ProductRepositoryImpl
    private lateinit var useCase: SyncProductsUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Real in-memory Room DB → real DAO + real transactions (no mocked runInTransaction).
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
        remote = ControllableFakeRemote()

        syncRepo = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
            movementDao = db.stockMovementDao(),
            cursorStore = com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore(),
        )

        val scheduler = mockk<com.are.distribuidora.workers.ProductSyncScheduler>(relaxed = true)
        val coordinator = com.are.distribuidora.workers.ProductSyncCoordinator(
            scheduler = scheduler,
            networkMonitor = FakeNetworkMonitor(true),
            firebaseAuth = mockAuthWithUser(),
            productDao = dao,
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
        )
        productRepo = ProductRepositoryImpl(productDao = dao, coordinator = coordinator)
        useCase = SyncProductsUseCase(
            repository = syncRepo,
            connectivityChecker = FakeConnectivityChecker(true),
            logger = FakeLogger(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ───────────────────────────── CRITERION A (the headline data-loss) ─────────────────────────────

    /**
     * THE bug (4.1 flavor): a not-yet-uploaded LOCAL edit must survive a downsync that brings the
     * stale remote doc at the SAME timestamp (the tie). Since 4.1 the stock edit is a pending
     * MOVEMENT, so the assertion is "stock local = remoto + pendientes" and the dirty price edit
     * (PENDING_UPDATE) is protected as before.
     */
    @Test
    fun `local edit (price) and pending stock movement survive a downsync at the same timestamp`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, updatedRemoteAt = 1000L)

        // Offline: user edits price (dirty row) AND registers an entrada voucher of 50 (movement).
        productRepo.save(domainProduct(id = "p1", stock = 50, updatedAt = 1000L, price = 12.0))
        db.stockMovementDao().insert(pendingMovement("m1", "p1", "ENTRADA", 50))
        dao.applyMovement("p1", +50)
        val dirty = dao.getById("p1")!!
        assertEquals(SyncStatus.PENDING_UPDATE, dirty.syncStatus)
        assertEquals(100, dirty.stock)
        assertEquals(1000L, dirty.updatedAt)

        // Stale remote at the same timestamp.
        syncRepo.syncDownstream()

        val after = dao.getById("p1")!!
        assertEquals("LOCAL EDIT WAS SILENTLY REVERTED BY DOWNSYNC", 100, after.stock)
        assertEquals(12.0, after.price, 0.0)
        assertEquals(SyncStatus.PENDING_UPDATE, after.syncStatus)
    }

    /** A SYNCING row (upload in flight) must never be overwritten by remote data. */
    @Test
    fun `downsync never overwrites a SYNCING row`() = runTest {
        dao.insert(dirtyEntity(id = "p2", stock = 100, updatedAt = 1000L, status = SyncStatus.SYNCING))
        remote.storage["p2"] = remoteProduct(id = "p2", stock = 50, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        val after = dao.getById("p2")!!
        assertEquals(100, after.stock)
        assertEquals(SyncStatus.SYNCING, after.syncStatus)
    }

    /** A CONFLICT row is local intent too: protect it. */
    @Test
    fun `downsync never overwrites a CONFLICT row`() = runTest {
        dao.insert(dirtyEntity(id = "p3", stock = 100, updatedAt = 1000L, status = SyncStatus.CONFLICT))
        remote.storage["p3"] = remoteProduct(id = "p3", stock = 50, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        assertEquals(100, dao.getById("p3")!!.stock)
        assertEquals(SyncStatus.CONFLICT, dao.getById("p3")!!.syncStatus)
    }

    // ───────────────────────────── CRITERION B (stock = remote + pending movements) ─────────────────────────────

    @Test
    fun `downsync rebuilds stock as remote plus pending movements and converges when they upload`() = runTest {
        dao.insert(syncedEntity(id = "p4", stock = 50, updatedAt = 1000L, name = "P4"))
        db.stockMovementDao().insert(pendingMovement("m4", "p4", "SALIDA", 8))
        dao.applyMovement("p4", -8) // local 42

        // Another phone sold 20 → remote 30 (its own movement already applied server-side).
        remote.storage["p4"] = remoteProduct(id = "p4", stock = 30, updatedRemoteAt = 2000L, name = "P4-new")
        syncRepo.syncDownstream()

        val a = dao.getById("p4")!!
        assertEquals("30 remoto - 8 pendiente", 22, a.stock)
        assertEquals("P4-new", a.name)

        // Our movement uploads: server applies increment(-8) → 22 and we mark it SYNCED.
        remote.storage["p4"] = remoteProduct(id = "p4", stock = 22, updatedRemoteAt = 3000L, name = "P4-new")
        db.stockMovementDao().markSynced(listOf("m4"), at = 3000L)
        syncRepo.syncDownstream()

        val b = dao.getById("p4")!!
        assertEquals("no double deduction after the movement is synced", 22, b.stock)
        assertEquals(3000L, b.updatedAt)
    }

    // ───────────────────────────── CRITERION C (stock null) ─────────────────────────────

    @Test
    fun `downsync preserves stock when remote stock field is absent`() = runTest {
        dao.insert(syncedEntity(id = "p5", stock = 50, updatedAt = 1000L, name = "P5"))

        remote.storage["p5"] = remoteProduct(id = "p5", stock = null, updatedRemoteAt = 2000L, name = "P5-renamed")
        syncRepo.syncDownstream()

        val a = dao.getById("p5")!!
        assertEquals("stock must be preserved when remote omits it", 50, a.stock)
        assertEquals("other fields still update", "P5-renamed", a.name)
        assertEquals(2000L, a.updatedAt)
    }

    // ───────────────────────────── CRITERION D (convergence) ─────────────────────────────

    @Test
    fun `pending edit converges after upload and is not reverted by later downsyncs`() = runTest {
        dao.insert(syncedEntity(id = "p6", stock = 50, updatedAt = 1000L, name = "P6"))
        remote.storage["p6"] = remoteProduct(id = "p6", stock = 50, updatedRemoteAt = 1000L, name = "P6")

        // User edits the price 10 -> 15 (stock is preserved by the repository; stock only moves via movements).
        productRepo.save(domainProduct(id = "p6", stock = 999, updatedAt = 1000L, name = "P6", price = 15.0))
        assertEquals(SyncStatus.PENDING_UPDATE, dao.getById("p6")!!.syncStatus)
        assertEquals("save() never writes stock as an absolute value", 50, dao.getById("p6")!!.stock)

        remote.assignServerTimestampOnUpload = 5000L
        val result = useCase()
        assertTrue(result.isSuccess)

        val afterCycle = dao.getById("p6")!!
        assertEquals("edit persisted through the upload→downsync cycle", 15.0, afterCycle.price, 0.0)
        assertEquals("converged to SYNCED (no stuck PENDING/CONFLICT)", SyncStatus.SYNCED, afterCycle.syncStatus)
        assertEquals("local adopted the fresh server timestamp", 5000L, afterCycle.updatedAt)
        assertEquals("remote keeps its own stock (client never uploads it)", 50, remote.storage["p6"]!!.stock)
        assertEquals(15.0, remote.storage["p6"]!!.price!!, 0.0)

        repeat(3) { syncRepo.syncDownstream() }
        val finalRow = dao.getById("p6")!!
        assertEquals(50, finalRow.stock)
        assertEquals(SyncStatus.SYNCED, finalRow.syncStatus)
    }

    // ───────────────────────────── helpers & fakes ─────────────────────────────

    private fun syncedEntity(
        id: String,
        stock: Int,
        updatedAt: Long,
        name: String = "P-$id",
    ) = ProductEntity(
        id = id, name = name, description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock,
        isActive = true, isDeleted = false,
        syncStatus = SyncStatus.SYNCED,
        createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun dirtyEntity(
        id: String,
        stock: Int,
        updatedAt: Long,
        status: SyncStatus,
    ) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock,
        isActive = true, isDeleted = false,
        syncStatus = status,
        createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun pendingMovement(id: String, productId: String, type: String, qty: Int) =
        com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity(
            id = id, productId = productId, productName = "P-$productId", type = type, quantity = qty,
            reason = "AJUSTE", orderId = null, note = null, createdBy = "u", createdByName = "U",
            createdAt = 1L, syncStatus = SyncStatus.PENDING_CREATE,
        )

    private fun domainProduct(
        id: String,
        stock: Int,
        updatedAt: Long,
        name: String = "P-$id",
        price: Double = 10.0,
    ) = Product(
        id = ProductId.of(id),
        name = name,
        description = null, category = null,
        price = Money.of(BigDecimal.valueOf(price)),
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = Quantity.of(stock),
        isActive = true, isDeleted = false,
        createdAt = updatedAt, updatedAt = updatedAt,
    )

    private fun remoteProduct(
        id: String,
        stock: Int?,
        updatedRemoteAt: Long,
        name: String = "P-$id",
        price: Double = 10.0,
    ) = RemoteProduct(
        id = id, name = name, description = null, category = null, price = price,
        imageUrl = null, barcode = null,
        stock = stock,
        isActive = true, isDeleted = false,
        createdRemoteAt = updatedRemoteAt, updatedRemoteAt = updatedRemoteAt,
    )

    /**
     * Controllable fake that mimics Firestore's downsync (whereGreaterThanOrEqualTo, ordered) and its
     * upload semantics: the server assigns `updatedAt` and NEVER takes `stock` from the payload (4.1).
     */
    private class ControllableFakeRemote : ProductRemoteDataSource {
        val storage = mutableMapOf<String, RemoteProduct>()
        /** When set, an upload stores the doc with this (fresh, authoritative) updatedAt. */
        var assignServerTimestampOnUpload: Long? = null

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val list = storage.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (list.isNotEmpty()) emit(list)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            val serverTs = assignServerTimestampOnUpload ?: product.updatedRemoteAt
            // 4.1: the client omits stock; the server keeps its own value.
            storage[product.id] = product.copy(stock = product.stock ?: storage[product.id]?.stock, updatedRemoteAt = serverTs)
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

    private class FakeNetworkMonitor(initialOnline: Boolean) : com.are.distribuidora.core.network.NetworkMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = state.asStateFlow()
        override fun isOnline(): Boolean = state.value
    }

    private fun mockAuthWithUser(): com.google.firebase.auth.FirebaseAuth {
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        every { auth.currentUser } returns mockk<com.google.firebase.auth.FirebaseUser>()
        return auth
    }
}
