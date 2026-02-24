package com.are.distribuidora.product

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.repository.ProductRepositoryImpl
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.SyncProductsUseCase
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.data.local.DistribuidoraDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.every
import androidx.room.withTransaction
import com.are.distribuidora.data.remote.model.RemoteProduct
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductSyncIntegrationTest {

    private lateinit var database: TestDatabase
    private lateinit var productDao: ProductDao
    
    private lateinit var fakeRemoteDataSource: FakeProductRemoteDataSource
    private lateinit var fakeConnectivityChecker: FakeConnectivityChecker
    private lateinit var fakeLogger: FakeLogger
    
    private lateinit var productRepository: ProductRepositoryImpl
    private lateinit var productSyncRepository: ProductSyncRepositoryImpl
    private lateinit var syncProductsUseCase: SyncProductsUseCase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestDatabase::class.java
        ).allowMainThreadQueries().build()

        productDao = database.productDao()
        fakeRemoteDataSource = FakeProductRemoteDataSource()
        fakeConnectivityChecker = FakeConnectivityChecker(true)
        fakeLogger = FakeLogger()

        // Mock Real Database for transaction support
        val mockDatabase = mockk<DistribuidoraDatabase>()
        coEvery { mockDatabase.runInTransaction<Any>(any<suspend () -> Any>()) } answers {
            val block = firstArg<suspend () -> Any>()
            runBlocking { block() }
        }

        productSyncRepository = ProductSyncRepositoryImpl(
            remote = fakeRemoteDataSource,
            local = productDao,
            database = mockDatabase,
            imageStorage = mockk(relaxed = true) // No se testea upload aquí
        )

        val scheduler = mockk<com.are.distribuidora.workers.ProductSyncScheduler>(relaxed = true)
        every { scheduler.scheduleOneTimeNow(any()) } returns Unit

        val coordinator = com.are.distribuidora.workers.ProductSyncCoordinator(
            scheduler = scheduler,
            networkMonitor = FakeNetworkMonitor(initialOnline = true),
            firebaseAuth = createMockAuthWithUser(),
            productDao = productDao,
            applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        )

        productRepository = ProductRepositoryImpl(productDao = productDao, coordinator = coordinator)

        syncProductsUseCase = SyncProductsUseCase(
            repository = productSyncRepository,
            connectivityChecker = fakeConnectivityChecker,
            logger = fakeLogger
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test_SyncFlow_RemoteToLocal_Create`() = runTest {
        // 1. Setup Remote
        val remoteProduct = RemoteProduct(
            id = "prod_1",
            name = "Remote Product",
            description = "Desc",
            category = "Cat",
            price = 100.0,
            imageUrl = "url",
            barcode = "123",
            stock = 10,
            comprometido = 0,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 1000L,
            updatedRemoteAt = 1000L
        )
        fakeRemoteDataSource.remoteStorage["prod_1"] = remoteProduct

        // 2. Sync
        val result = syncProductsUseCase()
        assertTrue(result.isSuccess)

        // 3. Verify Local
        val local = productDao.getById("prod_1")
        assertNotNull(local)
        assertEquals("Remote Product", local?.name)
        assertEquals(SyncStatus.SYNCED, local?.syncStatus)
        assertEquals(1000L, local?.updatedAt)
    }

    @Test
    fun `test_SyncFlow_LocalToRemote`() = runTest {
        // 1. Create Local PENDING_CREATE
        val product = Product(
            id = ProductId.of("prod_2"),
            name = "Local Product",
            price = Money.of(BigDecimal.valueOf(200.0)),
            stock = Quantity.of(5),
            description = "Desc",
            category = "Cat",
            imageUrl = "url",
            barcode = "456",
            createdAt = 2000L,
            updatedAt = 2000L,
        )
        // Ensure save marks as PENDING
        productRepository.save(product) 
        
        val pending = productDao.getById("prod_2")
        assertEquals(SyncStatus.PENDING_CREATE, pending?.syncStatus)

        // 2. Sync
        val result = syncProductsUseCase()
        assertTrue(result.isSuccess)

        // 3. Verify Remote
        assertTrue(fakeRemoteDataSource.remoteStorage.containsKey("prod_2"))
        val remote = fakeRemoteDataSource.remoteStorage["prod_2"]
        assertEquals("Local Product", remote?.name)

        // 4. Verify Local is now SYNCED
        val synced = productDao.getById("prod_2")
        assertEquals(SyncStatus.SYNCED, synced?.syncStatus)
    }

    @Test
    fun `test_ConflictResolution_RemoteWins`() = runTest {
        // 1. Setup Local Old as SYNCED (insert directo para simular estado limpio)
        val localEntity = com.are.distribuidora.data.local.entity.ProductEntity(
            id = "prod_3",
            name = "Old Local Name",
            description = "",
            category = "",
            price = 10.0,
            imageUrl = "",
            barcode = "",
            stock = 1,
            isActive = true,
            isDeleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = 1000L,
            updatedAt = 1000L,
            lastSyncedAt = 1000L
        )
        productDao.insert(localEntity)

        // 2. Setup Remote New
        val remoteProduct = RemoteProduct(
            id = "prod_3",
            name = "New Remote Name",
            price = 20.0,
            description = "",
            category = "",
            imageUrl = "",
            barcode = "",
            stock = 1,
            comprometido = 0,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 1000L,
            updatedRemoteAt = 3000L
        )
        fakeRemoteDataSource.remoteStorage["prod_3"] = remoteProduct

        // 3. Sync
        syncProductsUseCase()

        // 4. Verify Local Updated
        val local = productDao.getById("prod_3")
        assertEquals("New Remote Name", local?.name)
        assertEquals(3000L, local?.updatedAt)
    }

    // --- Fakes ---

    private class FakeConnectivityChecker(private val isOnline: Boolean) : ConnectivityChecker {
        override suspend fun isOnline(): Boolean = isOnline
    }

    private class FakeLogger : Logger {
        override fun d(tag: String, message: String) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }

    class FakeProductRemoteDataSource : ProductRemoteDataSource {
        val remoteStorage = mutableMapOf<String, RemoteProduct>()

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            emit(remoteStorage.values.filter { (it.updatedRemoteAt ?: 0L) >= timestamp })
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            remoteStorage[product.id] = product
        }

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            val p = remoteStorage[id]
            if (p != null) {
                remoteStorage[id] = p.copy(isDeleted = true, updatedRemoteAt = timestamp)
            }
        }
    }

    private class FakeNetworkMonitor(initialOnline: Boolean) : com.are.distribuidora.core.network.NetworkMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline: kotlinx.coroutines.flow.StateFlow<Boolean> = state.asStateFlow()
        override fun isOnline(): Boolean = state.value
    }

    private fun createMockAuthWithUser(): com.google.firebase.auth.FirebaseAuth {
        val auth = mockk<com.google.firebase.auth.FirebaseAuth>()
        val user = mockk<com.google.firebase.auth.FirebaseUser>()
        io.mockk.every { auth.currentUser } returns user
        return auth
    }

    // --- Database ---
    
    class Converters {
        @androidx.room.TypeConverter
        fun fromSyncStatus(value: SyncStatus?): String? = value?.name
        @androidx.room.TypeConverter
        fun toSyncStatus(value: String?): SyncStatus? = value?.let { SyncStatus.valueOf(it) }
    }

    @androidx.room.Database(
        entities = [
            ProductEntity::class,
            com.are.distribuidora.data.local.entity.ProductConflictEntity::class
        ],
        version = 1,
        exportSchema = false
    )
    @androidx.room.TypeConverters(Converters::class)
    abstract class TestDatabase : androidx.room.RoomDatabase() {
        abstract fun productDao(): ProductDao
    }
}
