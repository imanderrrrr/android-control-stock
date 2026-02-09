
package com.are.distribuidora.client

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.mapper.toDto
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.data.repository.ClientRepositoryImpl
import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.usecase.DeleteClientUseCase
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.client.domain.usecase.UpdateClientUseCase
import com.are.distribuidora.client.domain.validator.ClientValidator
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.core.network.NetworkMonitor
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.route.data.local.dao.RouteDao
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import javax.inject.Inject
import androidx.room.withTransaction

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClientSyncIntegrationTest {

    // Dependencies
    private lateinit var database: TestDatabase
    private lateinit var clientDao: ClientDao
    private lateinit var routeDao: RouteDao
    
    // Fakes
    private lateinit var fakeRemoteDataSource: FakeClientRemoteDataSource
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var fakeRouteSyncRepository: com.are.distribuidora.route.domain.repository.RouteSyncRepository

    // Real implementations
    private lateinit var clientRepository: ClientRepositoryImpl
    private lateinit var clientSyncRepository: ClientSyncRepositoryImpl
    
    // Use Cases
    private lateinit var updateClientUseCase: UpdateClientUseCase
    private lateinit var deleteClientUseCase: DeleteClientUseCase
    private lateinit var syncClientsUseCase: SyncClientsUseCase

    @Before
    fun setup() {
        // 1. Setup InMemory Database
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestDatabase::class.java
        ).allowMainThreadQueries().build()

        clientDao = database.clientDao()
        routeDao = database.routeDao()

        // 2. Setup Fakes
        fakeRemoteDataSource = FakeClientRemoteDataSource()
        
        // Monitors: One for manual sync (Online), one for Coordinator (Offline to disable auto-sync)
        val testOnlineMonitor = FakeNetworkMonitor(initialState = true)
        val coordinatorOfflineMonitor = FakeNetworkMonitor(initialState = false)

        // 3. Setup Components
        val localDataSource = ClientLocalDataSource(clientDao)
        val validator = ClientValidator()
        val mapper = ClientRemoteMapper()

        // Mock Database for Transaction support
        val mockDatabase = mockk<com.are.distribuidora.data.local.DistribuidoraDatabase>()
        coEvery { mockDatabase.withTransaction<Any>(any()) } answers {
            val block = firstArg<suspend () -> Any>()
            runBlocking { block() }
        }

        clientSyncRepository = ClientSyncRepositoryImpl(
            remote = fakeRemoteDataSource,
            local = localDataSource,
            mapper = mapper,
            database = mockDatabase
        )
        
        fakeRouteSyncRepository = mockk(relaxed = true)

        // Setup Coordinator (Real implementation but neutralized)
        // We need a SyncUseCase for the coordinator, but we want it to NOT run.
        // Passing offline monitor ensures it doesn't trigger unexpectedly.
        val syncUseCaseForCoordinator = SyncClientsUseCase(
            repository = clientSyncRepository,
            routeSyncRepository = fakeRouteSyncRepository,
            networkMonitor = coordinatorOfflineMonitor
        )
        
        val testScope = CoroutineScope(UnconfinedTestDispatcher())
        
        val mockAuth = io.mockk.mockk<FirebaseAuth>(relaxed = true)
        val mockUser = io.mockk.mockk<FirebaseUser>()
        io.mockk.every { mockAuth.currentUser } returns mockUser

        val realCoordinator = ClientSyncCoordinator(
            syncUseCase = syncUseCaseForCoordinator,
            networkMonitor = coordinatorOfflineMonitor,
            firebaseAuth = mockAuth,
            applicationScope = testScope
        )

        clientRepository = ClientRepositoryImpl(
            local = localDataSource,
            remote = fakeRemoteDataSource,
            sync = clientSyncRepository,
            validator = validator,
            coordinator = realCoordinator
        )

        // 4. Setup Use Cases for Test
        updateClientUseCase = UpdateClientUseCase(clientRepository)
        deleteClientUseCase = DeleteClientUseCase(clientRepository)
        
        // This is the one we invoke manually in validation, so it must be online
        syncClientsUseCase = SyncClientsUseCase(
            repository = clientSyncRepository,
            routeSyncRepository = fakeRouteSyncRepository,
            networkMonitor = testOnlineMonitor
        )
        fakeNetworkMonitor = testOnlineMonitor // Keep reference if needed for assertions
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `testCase1_UpdateClient_Flow`() = runTest {
        // Setup Route (FK constraint)
        val routeId = "route_1"
        setupRoute(routeId)

        // 1. Create locally (PENDING_CREATE)
        val client = createValidClient(id = "client_1", routeId = routeId, name = "Original Name")
        clientRepository.create(client) // Directly calling repo create logic simulating UseCase

        val localCreated = clientDao.getById("client_1")
        assertNotNull(localCreated)
        assertEquals(SyncStatus.PENDING_CREATE, localCreated?.syncStatus)

        // 2. Sync (Create Remote)
        syncClientsUseCase()

        // Verify synced
        val syncedClient = clientDao.getById("client_1")
        assertEquals(SyncStatus.SYNCED, syncedClient?.syncStatus)
        assertTrue(fakeRemoteDataSource.remoteStorage.containsKey("client_1"))
        assertEquals("Original Name", fakeRemoteDataSource.remoteStorage["client_1"]?.name)
        val initialLastSyncedAt = syncedClient?.lastSyncedAt ?: 0L
        assertTrue(initialLastSyncedAt > 0)

        // 3. Update Client (Local)
        val domainClient = syncedClient!!.toDomain()
        val updatedClient = domainClient.copy(name = "Updated Name")
        
        // Ensure some time passes for timestamps
        // runTest controls time, but System.currentTimeMillis() needs real time or mock. 
        // For integration test relying on logic, we just check inequality if logic updates it.
        
        updateClientUseCase(updatedClient)

        val pendingUpdate = clientDao.getById("client_1")
        assertEquals(SyncStatus.PENDING_UPDATE, pendingUpdate?.syncStatus)
        assertTrue("UpdatedAt should update on edit", pendingUpdate!!.updatedAt > domainClient.updatedAt)

        // 4. Sync (Update Remote)
        syncClientsUseCase()

        // Verify final state
        val finalClient = clientDao.getById("client_1")
        assertEquals(SyncStatus.SYNCED, finalClient?.syncStatus)
        assertEquals("Updated Name", fakeRemoteDataSource.remoteStorage["client_1"]?.name)
        assertTrue("LastSyncedAt should update on sync", finalClient!!.lastSyncedAt!! > initialLastSyncedAt)
    }

    @Test
    fun `testCase2_ActivateDeactivate_Flow`() = runTest {
        val routeId = "route_1"
        setupRoute(routeId)

        // Initial State: Synced Client
        val client = createValidClient(id = "client_2", routeId = routeId, isActive = true)
        clientRepository.create(client)
        syncClientsUseCase()
        
        val initialSynced = clientDao.getById("client_2")
        assertEquals(SyncStatus.SYNCED, initialSynced?.syncStatus)
        assertTrue(initialSynced?.isActive == true)

        // 1. Deactivate
        val domainClient = initialSynced!!.toDomain()
        updateClientUseCase(domainClient.copy(isActive = false))

        // Verify Pending Update
        val pendingUpdate = clientDao.getById("client_2")
        assertEquals(SyncStatus.PENDING_UPDATE, pendingUpdate?.syncStatus)
        assertEquals(false, pendingUpdate?.isActive)

        // 2. Sync
        syncClientsUseCase()

        // Verify Remote & Local
        val remoteClient = fakeRemoteDataSource.remoteStorage["client_2"]
        assertEquals(false, remoteClient?.isActive)
        
        val finalClient = clientDao.getById("client_2")
        assertEquals(SyncStatus.SYNCED, finalClient?.syncStatus)
        assertEquals(false, finalClient?.isActive)
    }

    @Test
    fun `testCase3_Delete_Flow`() = runTest {
        val routeId = "route_1"
        setupRoute(routeId)

        // Initial State: Synced Client
        val client = createValidClient(id = "client_3", routeId = routeId)
        clientRepository.create(client)
        syncClientsUseCase()
        
        assertTrue(fakeRemoteDataSource.remoteStorage.containsKey("client_3"))

        // 1. Delete
        deleteClientUseCase("client_3")

        // Verify Pending Delete
        val pendingDelete = clientDao.getById("client_3")
        assertNotNull(pendingDelete)
        assertEquals(SyncStatus.PENDING_DELETE, pendingDelete?.syncStatus)

        // 2. Sync
        syncClientsUseCase()

        // Verify Remote Soft Deleted (Tombstone)
        val remoteClient = fakeRemoteDataSource.remoteStorage["client_3"]
        assertNotNull(remoteClient)
        assertTrue(remoteClient!!.isDeleted)

        // Verify Local Deleted
        val localDeleted = clientDao.getById("client_3")
        assertNull("Client should be removed from DB after successful delete sync", localDeleted)
    }

    // --- Helpers ---

    private suspend fun setupRoute(routeId: String) {
        routeDao.upsert(
            RouteEntity(
                id = routeId,
                name = "Test Route",
                deliveryDay = 1,
                syncStatus = SyncStatus.SYNCED,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun createValidClient(id: String, routeId: String, name: String = "Test Client", isActive: Boolean = true): Client {
        return Client(
            id = id,
            name = name,
            phone = "123",
            address = "Address",
            latitude = 0.0,
            longitude = 0.0,
            maxOrderAmountInCents = 1000,
            isActive = isActive,
            isDeleted = false,
            routeId = routeId,
            syncStatus = SyncStatus.PENDING_CREATE,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            createdBy = "tester",
            lastModifiedBy = "tester"
        )
    }

    // --- Internal Fakes / Database ---

    class Converters {
        @androidx.room.TypeConverter
        fun fromSyncStatus(value: SyncStatus?): String? {
            return value?.name
        }

        @androidx.room.TypeConverter
        fun toSyncStatus(value: String?): SyncStatus? {
            return value?.let { SyncStatus.valueOf(it) }
        }
    }

    @androidx.room.Database(entities = [ClientEntity::class, RouteEntity::class], version = 1, exportSchema = false)
    @androidx.room.TypeConverters(Converters::class)
    abstract class TestDatabase : androidx.room.RoomDatabase() {
        abstract fun clientDao(): ClientDao
        abstract fun routeDao(): RouteDao
    }

    private fun ClientEntity.toDomain() = Client(
        id = id,
        name = name,
        phone = phone,
        address = address,
        latitude = latitude,
        longitude = longitude,
        maxOrderAmountInCents = maxOrderAmountInCents,
        isActive = isActive,
        isDeleted = isDeleted,
        routeId = routeId,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        lastModifiedBy = lastModifiedBy
    )

    // Copied from androidTest/../FakeClientRemoteDataSource.kt and adapted if needed
    class FakeClientRemoteDataSource : ClientRemoteDataSource {
        val remoteStorage = mutableMapOf<String, ClientDto>()

        override suspend fun uploadClient(client: ClientDto) {
            remoteStorage[client.id] = client
        }

        override suspend fun softDeleteClient(id: String) {
            val existing = remoteStorage[id]
            if (existing != null) {
                remoteStorage[id] = existing.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
            }
        }

        override suspend fun fetchClients(limit: Int): List<ClientDto> {
            return remoteStorage.values.toList()
        }

        override suspend fun fetchClientsAfter(timestamp: Long): List<ClientDto> {
             return remoteStorage.values.filter { it.updatedAt >= timestamp }.sortedBy { it.updatedAt }
        }

        override suspend fun getClientById(id: String): ClientDto? {
            return remoteStorage[id]
        }
    }

    class FakeNetworkMonitor(initialState: Boolean) : NetworkMonitor {
        private val _isOnline = MutableStateFlow(initialState)
        override val isOnline: Flow<Boolean> = _isOnline.asStateFlow()
        override fun isOnline(): Boolean = _isOnline.value
    }
}
