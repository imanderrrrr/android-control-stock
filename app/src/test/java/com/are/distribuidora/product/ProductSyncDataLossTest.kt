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
 * a local STOCK edit (and resetting comprometido / zeroing stock).
 *
 * The decisive proof is [downsync does NOT revert a PENDING_UPDATE stock edit on an equal-timestamp tie]:
 * it FAILS on the pre-fix code (stock reverts 100 -> 50) and PASSES after the fix.
 *
 * Acceptance criteria covered:
 *  A — a not-yet-durably-synced local row is never overwritten by remote (PENDING_UPDATE tie,
 *      SYNCING, CONFLICT).
 *  B — comprometido is preserved across sync (only changes when the remote explicitly carries it).
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
     * THE bug: user edits stock offline; the edit is PENDING_UPDATE and (per ProductRepository.save)
     * keeps the OLD updatedAt. A downsync then sees the stale remote doc at the SAME timestamp and,
     * pre-fix, the strict `>` conflict guard misses the tie and LWW's `>=` lets stale remote win.
     *
     * Pre-fix: stock reverts 100 -> 50 (and comprometido 8 -> 0). Post-fix: edit survives.
     */
    @Test
    fun `downsync does NOT revert a PENDING_UPDATE stock edit on an equal-timestamp tie`() = runTest {
        // GIVEN a durably-synced product: stock 50, comprometido 8, updatedAt 1000.
        dao.insert(syncedEntity(id = "p1", stock = 50, comprometido = 8, updatedAt = 1000L))

        // WHEN the user edits stock 50 -> 100 (realistic path through the repository).
        productRepo.save(domainProduct(id = "p1", stock = 100, comprometido = 8, updatedAt = 1000L))

        // Precondition that sets up the tie: the edit is PENDING_UPDATE and PRESERVES updatedAt=1000.
        val afterEdit = dao.getById("p1")!!
        assertEquals(SyncStatus.PENDING_UPDATE, afterEdit.syncStatus)
        assertEquals(100, afterEdit.stock)
        assertEquals("save() must preserve the old updatedAt (this creates the tie)", 1000L, afterEdit.updatedAt)

        // AND the remote still holds the STALE value at the SAME timestamp (the tie) + omits comprometido.
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, comprometido = null, updatedRemoteAt = 1000L)

        // WHEN a downsync runs (INVENTORY_OPEN / reconnect).
        syncRepo.syncDownstream()

        // THEN the user's edit MUST survive.
        val after = dao.getById("p1")!!
        assertEquals("STOCK EDIT WAS SILENTLY REVERTED BY DOWNSYNC", 100, after.stock)
        assertEquals("comprometido must not be reset", 8, after.comprometido)
        assertEquals("row stays dirty so uploadPendingProducts can push it", SyncStatus.PENDING_UPDATE, after.syncStatus)
    }

    /** Criterion A(b): a SYNCING (in-flight upload) row must not be overwritten — even by a strictly NEWER remote. */
    @Test
    fun `downsync does NOT overwrite a SYNCING row even when remote is newer`() = runTest {
        dao.insert(dirtyEntity(id = "p2", stock = 100, comprometido = 4, updatedAt = 1000L, status = SyncStatus.SYNCING))
        remote.storage["p2"] = remoteProduct(id = "p2", stock = 50, comprometido = null, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        val after = dao.getById("p2")!!
        assertEquals(100, after.stock)
        assertEquals(SyncStatus.SYNCING, after.syncStatus)
    }

    /** Criterion A(b): a CONFLICT row keeps the local copy (the documented contract) — never overwritten. */
    @Test
    fun `downsync does NOT overwrite a CONFLICT row`() = runTest {
        dao.insert(dirtyEntity(id = "p3", stock = 100, comprometido = 2, updatedAt = 1000L, status = SyncStatus.CONFLICT))
        remote.storage["p3"] = remoteProduct(id = "p3", stock = 50, comprometido = null, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        val after = dao.getById("p3")!!
        assertEquals(100, after.stock)
        assertEquals(SyncStatus.CONFLICT, after.syncStatus)
    }

    // ───────────────────────────── CRITERION B (comprometido) ─────────────────────────────

    /**
     * On a CLEAN (SYNCED) row that the remote legitimately updates, comprometido must be preserved
     * when the remote omits it, and adopted when the remote explicitly carries it.
     */
    @Test
    fun `downsync preserves comprometido when remote omits it and adopts it when present`() = runTest {
        dao.insert(syncedEntity(id = "p4", stock = 50, comprometido = 8, updatedAt = 1000L, name = "P4"))

        // Remote update WITHOUT comprometido (the normal case — comprometido is never written to Firestore).
        remote.storage["p4"] = remoteProduct(id = "p4", stock = 70, comprometido = null, updatedRemoteAt = 2000L, name = "P4-new")
        syncRepo.syncDownstream()

        val a = dao.getById("p4")!!
        assertEquals("remote wins on a clean SYNCED row", 70, a.stock)
        assertEquals("P4-new", a.name)
        assertEquals("comprometido must be preserved, not reset to 0", 8, a.comprometido)
        assertEquals(2000L, a.updatedAt)
        assertEquals(SyncStatus.SYNCED, a.syncStatus)

        // Remote update WITH an explicit comprometido → adopt it.
        remote.storage["p4"] = remoteProduct(id = "p4", stock = 90, comprometido = 3, updatedRemoteAt = 3000L, name = "P4-new")
        syncRepo.syncDownstream()

        val b = dao.getById("p4")!!
        assertEquals(3, b.comprometido)
        assertEquals(90, b.stock)
    }

    // ───────────────────────────── CRITERION C (stock null) ─────────────────────────────

    /** A partial/merge remote write that omits `stock` must not zero the local stock. */
    @Test
    fun `downsync preserves stock when remote stock field is absent`() = runTest {
        dao.insert(syncedEntity(id = "p5", stock = 50, comprometido = 0, updatedAt = 1000L, name = "P5"))

        remote.storage["p5"] = remoteProduct(id = "p5", stock = null, comprometido = null, updatedRemoteAt = 2000L, name = "P5-renamed")
        syncRepo.syncDownstream()

        val a = dao.getById("p5")!!
        assertEquals("stock must be preserved when remote omits it", 50, a.stock)
        assertEquals("other fields still update", "P5-renamed", a.name)
        assertEquals(2000L, a.updatedAt)
    }

    // ───────────────────────────── CRITERION D (convergence) ─────────────────────────────

    /**
     * After the offline edit DOES upload, the app converges: the value persists, the row becomes
     * SYNCED, comprometido survives end-to-end, and repeated downsyncs never revert it (no loop).
     */
    @Test
    fun `pending edit converges after upload and is not reverted by later downsyncs`() = runTest {
        dao.insert(syncedEntity(id = "p6", stock = 50, comprometido = 8, updatedAt = 1000L, name = "P6"))
        remote.storage["p6"] = remoteProduct(id = "p6", stock = 50, comprometido = null, updatedRemoteAt = 1000L, name = "P6")

        // User edits 50 -> 100.
        productRepo.save(domainProduct(id = "p6", stock = 100, comprometido = 8, updatedAt = 1000L, name = "P6"))
        assertEquals(SyncStatus.PENDING_UPDATE, dao.getById("p6")!!.syncStatus)

        // Full cycle: upload (server assigns a fresh authoritative timestamp) THEN downsync.
        remote.assignServerTimestampOnUpload = 5000L
        val result = useCase()
        assertTrue(result.isSuccess)

        val afterCycle = dao.getById("p6")!!
        assertEquals("edit persisted through the upload→downsync cycle", 100, afterCycle.stock)
        assertEquals("converged to SYNCED (no stuck PENDING/CONFLICT)", SyncStatus.SYNCED, afterCycle.syncStatus)
        assertEquals("comprometido survived the full real cycle", 8, afterCycle.comprometido)
        assertEquals("local adopted the fresh server timestamp", 5000L, afterCycle.updatedAt)
        assertEquals("remote now holds the user's value", 100, remote.storage["p6"]!!.stock)

        // Repeated downsyncs must remain stable (no infinite revert / no loop).
        repeat(3) { syncRepo.syncDownstream() }
        val finalRow = dao.getById("p6")!!
        assertEquals(100, finalRow.stock)
        assertEquals(SyncStatus.SYNCED, finalRow.syncStatus)
        assertEquals(8, finalRow.comprometido)
    }

    // ───────────────────────────── helpers & fakes ─────────────────────────────

    private fun syncedEntity(
        id: String,
        stock: Int,
        comprometido: Int,
        updatedAt: Long,
        name: String = "P-$id",
    ) = ProductEntity(
        id = id, name = name, description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, comprometido = comprometido,
        isActive = true, isDeleted = false,
        syncStatus = SyncStatus.SYNCED,
        createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun dirtyEntity(
        id: String,
        stock: Int,
        comprometido: Int,
        updatedAt: Long,
        status: SyncStatus,
    ) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, comprometido = comprometido,
        isActive = true, isDeleted = false,
        syncStatus = status,
        createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun domainProduct(
        id: String,
        stock: Int,
        comprometido: Int,
        updatedAt: Long,
        name: String = "P-$id",
    ) = Product(
        id = ProductId.of(id),
        name = name,
        description = null, category = null,
        price = Money.of(BigDecimal.valueOf(10.0)),
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = Quantity.of(stock),
        comprometido = comprometido,
        isActive = true, isDeleted = false,
        createdAt = updatedAt, updatedAt = updatedAt,
    )

    private fun remoteProduct(
        id: String,
        stock: Int?,
        comprometido: Int?,
        updatedRemoteAt: Long,
        name: String = "P-$id",
        price: Double = 10.0,
    ) = RemoteProduct(
        id = id, name = name, description = null, category = null, price = price,
        imageUrl = null, barcode = null,
        stock = stock, comprometido = comprometido,
        isActive = true, isDeleted = false,
        createdRemoteAt = updatedRemoteAt, updatedRemoteAt = updatedRemoteAt,
    )

    /**
     * Controllable fake that mimics Firestore's downsync (whereGreaterThanOrEqualTo, ordered) and its
     * upload semantics: the server assigns `updatedAt`, and `comprometido` is NOT persisted remotely.
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
            // Firestore never stores comprometido → drop it, exactly like FirestoreProductDataSource.
            storage[product.id] = product.copy(comprometido = null, updatedRemoteAt = serverTs)
        }

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
