package com.are.distribuidora.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.local.mapper.toEntity
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.remote.model.RemoteProduct
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductSyncIntegrationTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
    private lateinit var remote: FakeProductRemoteDataSource
    private lateinit var repo: ProductSyncRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
        remote = FakeProductRemoteDataSource()
        repo = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `fetchRemoteProducts downloads and saves new product`() = runTest {
        // Setup Remote
        val remoteProduct = RemoteProduct(
            id = "p1",
            name = "Coca Cola",
            description = "Refresco",
            category = "Bebidas",
            price = 15.0,
            imageUrl = null,
            barcode = "123",
            stock = 100,
            comprometido = 0,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 1000L,
            updatedRemoteAt = 1000L
        )
        remote.products = listOf(remoteProduct)

        // Action
        val fetched = repo.fetchRemoteProducts()
        repo.saveLocalProducts(fetched)

        // Assert
        val local = dao.getById("p1")
        assertNotNull(local)
        assertEquals("Coca Cola", local?.name)
        assertEquals(SyncStatus.SYNCED, local?.syncStatus)
        assertEquals(1000L, local?.updatedAt)
    }

    @Test
    fun `conflict LWW - Remote Wins (Newer Timestamp)`() = runTest {
        // Setup Local (Old)
        val localEntity = ProductEntity(
            id = "p1",
            name = "Old Name",
            description = null,
            category = null,
            price = 10.0,
            imageUrl = null,
            barcode = null,
            stock = 10,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = 100L,
            updatedAt = 100L,
            lastSyncedAt = 100L
        )
        dao.insert(localEntity)

        // Setup Remote (Newer)
        val remoteProduct = RemoteProduct(
            id = "p1",
            name = "New Name", // Changed
            description = null,
            category = null,
            price = 20.0, // Changed
            imageUrl = null,
            barcode = null,
            stock = 10,
            comprometido = 0,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 100L,
            updatedRemoteAt = 200L // Newer than 100L
        )
        remote.products = listOf(remoteProduct)

        // Action
        val fetched = repo.fetchRemoteProducts()
        repo.saveLocalProducts(fetched)

        // Assert
        val updatedLocal = dao.getById("p1")
        assertEquals("New Name", updatedLocal?.name)
        assertEquals(20.0, updatedLocal?.price!!, 0.0)
        assertEquals(200L, updatedLocal.updatedAt)
    }

    @Test
    fun `conflict LWW - Local Wins (Older Remote)`() = runTest {
        // Setup Local (Newer)
        val localEntity = ProductEntity(
            id = "p1",
            name = "Local Name",
            description = null,
            category = null,
            price = 50.0,
            imageUrl = null,
            barcode = null,
            stock = 10,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_UPDATE,
            createdAt = 100L,
            updatedAt = 300L, // Newer than remote
            lastSyncedAt = 100L
        )
        dao.insert(localEntity)

        // Setup Remote (Older)
        val remoteProduct = RemoteProduct(
            id = "p1",
            name = "Remote Name",
            description = null,
            category = null,
            price = 10.0,
            imageUrl = null,
            barcode = null,
            stock = 10,
            comprometido = 0,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 100L,
            updatedRemoteAt = 200L // Older than 300L
        )
        remote.products = listOf(remoteProduct)

        // Action
        val fetched = repo.fetchRemoteProducts()
        repo.saveLocalProducts(fetched)

        // Assert
        val finalLocal = dao.getById("p1")
        assertEquals("Local Name", finalLocal?.name) // Should NOT change
        assertEquals(300L, finalLocal?.updatedAt)
    }

    @Test
    fun `uploadPendingProducts uploads successfully`() = runTest {
        // Setup Local Pending
        val pendingEntity = ProductEntity(
            id = "p_pending",
            name = "Pending Product",
            description = null,
            category = null,
            price = 100.0,
            imageUrl = null,
            barcode = null,
            stock = 5,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_CREATE,
            createdAt = 500L,
            updatedAt = 500L,
            lastSyncedAt = 0L
        )
        dao.insert(pendingEntity)

        // Action
        repo.uploadPendingProducts()

        // Assert
        // 1. Remote received upload
        assertEquals(1, remote.uploadedProducts.size)
        assertEquals("p_pending", remote.uploadedProducts[0].id)

        // 2. Local marked SYNCED
        val local = dao.getById("p_pending")
        assertEquals(SyncStatus.SYNCED, local?.syncStatus)
        assertTrue(local?.lastSyncedAt!! > 0)
    }

    @Test
    fun `uploadPendingProducts network failure reverts SYNCING to PENDING`() = runTest {
        val pendingEntity = ProductEntity(
            id = "p_fail",
            name = "Pending Product",
            description = null,
            category = null,
            price = 100.0,
            imageUrl = null,
            barcode = null,
            stock = 5,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_UPDATE,
            createdAt = 500L,
            updatedAt = 500L,
            lastSyncedAt = 111L
        )
        dao.insert(pendingEntity)

        remote.failUploadsWith = IOException("No network")

        repo.uploadPendingProducts()

        val local = dao.getById("p_fail")
        assertEquals(SyncStatus.PENDING_UPDATE, local?.syncStatus)
    }

    @Test
    fun `uploadPendingProducts mapping null does not leave SYNCING`() = runTest {
        // name es NOT NULL/blank en el modelo de dominio; si lo dejamos blank, toDomainOrNull() debera devolver null.
        val pendingEntity = ProductEntity(
            id = "p_bad",
            name = "", // fuerza mapping null
            description = null,
            category = null,
            price = 100.0,
            imageUrl = null,
            barcode = null,
            stock = 5,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_UPDATE,
            createdAt = 500L,
            updatedAt = 500L,
            lastSyncedAt = 222L
        )
        dao.insert(pendingEntity)

        repo.uploadPendingProducts()

        val local = dao.getById("p_bad")
        assertEquals(SyncStatus.PENDING_UPDATE, local?.syncStatus)
    }

    // --- Helpers & Fakes ---

    class FakeProductRemoteDataSource : ProductRemoteDataSource {
        var products: List<RemoteProduct> = emptyList()
        val uploadedProducts = mutableListOf<RemoteProduct>()
        var failUploadsWith: Exception? = null

        override fun fetchProductsFlow(
            timestamp: Long,
            lastId: String?,
            batchSize: Long
        ): Flow<List<RemoteProduct>> = flow {
            emit(products.filter { (it.updatedRemoteAt ?: 0L) >= timestamp })
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            failUploadsWith?.let { throw it }
            uploadedProducts.add(product)
        }

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            // No-op
        }
    }
}
