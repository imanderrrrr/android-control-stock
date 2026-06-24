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
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression suite for the ORDER-DRIVEN stock data-loss bug (fix "A1"/"A2").
 *
 * Bug: when a pedido deducts product stock via [ProductDao.deductStockAndCommit] /
 * [ProductDao.restoreStock], the product row was left SYNCED. So (a) the deduction was never
 * uploaded (getPending only takes PENDING_*), and (b) the next downsync was NOT protected by the
 * dirty-row guard and reverted the stock to the stale remote value ("hago un pedido y al ratito
 * vuelve el stock").
 *
 * Fix A1: the stock DAO ops now mark the product PENDING_UPDATE (preserving PENDING_CREATE/
 * PENDING_DELETE). Fix A2: markSynced only fires while the row is still SYNCING, so a concurrent
 * edit during an upload is never silently cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductOrderStockSyncTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
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
        remote = FakeRemote()
        syncRepo = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
        )
        useCase = SyncProductsUseCase(
            repository = syncRepo,
            connectivityChecker = FakeConnectivityChecker(true),
            logger = FakeLogger(),
        )
    }

    @After
    fun tearDown() = db.close()

    // ───────────────────────────── A1 ─────────────────────────────

    /** THE order-stock bug: a deduction must be marked dirty AND survive a downsync. */
    @Test
    fun `order deduction marks PENDING_UPDATE and downsync does NOT revert it`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, comprometido = 0, updatedAt = 1000L))

        // What createPedido does to the product stock (deduct 10).
        dao.deductStockAndCommit("p1", 10)

        val afterDeduct = dao.getById("p1")!!
        assertEquals("stock deducted", 40, afterDeduct.stock)
        assertEquals("deduction must mark the product dirty so it uploads", SyncStatus.PENDING_UPDATE, afterDeduct.syncStatus)

        // Remote still holds the stale pre-order stock at the SAME timestamp (the revert trap).
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, comprometido = null, updatedRemoteAt = 1000L)
        syncRepo.syncDownstream()

        val after = dao.getById("p1")!!
        assertEquals("STOCK FROM THE ORDER WAS SILENTLY REVERTED BY DOWNSYNC", 40, after.stock)
        assertEquals(SyncStatus.PENDING_UPDATE, after.syncStatus)
    }

    /** The deduction must converge: after upload, the remote holds the new (lower) stock. */
    @Test
    fun `order deduction converges to remote after the upload cycle`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 50, comprometido = 0, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", stock = 50, comprometido = null, updatedRemoteAt = 1000L)

        dao.deductStockAndCommit("p1", 10) // 50 -> 40

        remote.assignServerTimestampOnUpload = 5000L
        assertTrue(useCase().isSuccess)

        val after = dao.getById("p1")!!
        assertEquals(40, after.stock)
        assertEquals(SyncStatus.SYNCED, after.syncStatus)
        assertEquals("remote must now hold the deducted stock", 40, remote.storage["p1"]!!.stock)

        // Stable across repeated downsyncs (no revert loop).
        repeat(3) { syncRepo.syncDownstream() }
        assertEquals(40, dao.getById("p1")!!.stock)
    }

    /** restoreStock (editing/deleting a pedido) must also mark the product dirty. */
    @Test
    fun `restoreStock marks PENDING_UPDATE`() = runTest {
        dao.insert(syncedEntity(id = "p1", stock = 40, comprometido = 5, updatedAt = 1000L))

        dao.restoreStock("p1", 10) // stock 40->50, comprometido MAX(0,5-10)=0

        val after = dao.getById("p1")!!
        assertEquals(50, after.stock)
        assertEquals(0, after.comprometido)
        assertEquals(SyncStatus.PENDING_UPDATE, after.syncStatus)
    }

    /** A product still pending creation must keep PENDING_CREATE (not be downgraded to UPDATE). */
    @Test
    fun `deduction preserves PENDING_CREATE`() = runTest {
        dao.insert(dirtyEntity(id = "p1", stock = 50, comprometido = 0, updatedAt = 0L, status = SyncStatus.PENDING_CREATE))

        dao.deductStockAndCommit("p1", 10)

        assertEquals(SyncStatus.PENDING_CREATE, dao.getById("p1")!!.syncStatus)
        assertEquals(40, dao.getById("p1")!!.stock)
    }

    // ───────────────────────────── A2 ─────────────────────────────

    /** markSynced must NOT clobber an edit made while the upload was in flight. */
    @Test
    fun `markSynced does not clobber a concurrent edit (SYNCING guard)`() = runTest {
        dao.insert(dirtyEntity(id = "p1", stock = 50, comprometido = 0, updatedAt = 1000L, status = SyncStatus.SYNCING))

        // Concurrent edit during the upload flips the row to PENDING_UPDATE with new stock.
        dao.update(dao.getById("p1")!!.copy(syncStatus = SyncStatus.PENDING_UPDATE, stock = 99))

        // Upload completes and tries to mark it synced — the guard must make this a no-op.
        dao.markSynced("p1", lastSyncedAt = 2000L)

        val after = dao.getById("p1")!!
        assertEquals("concurrent edit's dirty flag must survive", SyncStatus.PENDING_UPDATE, after.syncStatus)
        assertEquals("concurrent edit's value must survive", 99, after.stock)
    }

    /** Normal case: a SYNCING row is marked SYNCED by a completing upload. */
    @Test
    fun `markSynced transitions SYNCING to SYNCED normally`() = runTest {
        dao.insert(dirtyEntity(id = "p2", stock = 50, comprometido = 0, updatedAt = 1000L, status = SyncStatus.SYNCING))

        dao.markSynced("p2", lastSyncedAt = 2000L)

        assertEquals(SyncStatus.SYNCED, dao.getById("p2")!!.syncStatus)
    }

    // ───────────────────────────── helpers & fakes ─────────────────────────────

    private fun syncedEntity(id: String, stock: Int, comprometido: Int, updatedAt: Long) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, comprometido = comprometido, isActive = true, isDeleted = false,
        syncStatus = SyncStatus.SYNCED, createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun dirtyEntity(id: String, stock: Int, comprometido: Int, updatedAt: Long, status: SyncStatus) = ProductEntity(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, imageLocalUri = null, barcode = null,
        stock = stock, comprometido = comprometido, isActive = true, isDeleted = false,
        syncStatus = status, createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
    )

    private fun remoteProduct(id: String, stock: Int?, comprometido: Int?, updatedRemoteAt: Long) = RemoteProduct(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, barcode = null, stock = stock, comprometido = comprometido,
        isActive = true, isDeleted = false, createdRemoteAt = updatedRemoteAt, updatedRemoteAt = updatedRemoteAt,
    )

    private class FakeRemote : ProductRemoteDataSource {
        val storage = mutableMapOf<String, RemoteProduct>()
        var assignServerTimestampOnUpload: Long? = null

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val list = storage.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (list.isNotEmpty()) emit(list)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            val serverTs = assignServerTimestampOnUpload ?: product.updatedRemoteAt
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
}
