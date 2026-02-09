package com.are.distribuidora.client

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.client.fakes.FakeClientRemoteDataSource
import com.are.distribuidora.client.fakes.FakeNetworkMonitor
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ClientOfflineRecoveryTest {

    private lateinit var database: DistribuidoraDatabase
    private lateinit var localDataSource: ClientLocalDataSource
    private lateinit var remoteDataSource: FakeClientRemoteDataSource
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var coordinator: ClientSyncCoordinator

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DistribuidoraDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        localDataSource = ClientLocalDataSource(database.clientDao())
        remoteDataSource = FakeClientRemoteDataSource()
        val mapper = ClientRemoteMapper()

        val repository = ClientSyncRepositoryImpl(
            remote = remoteDataSource,
            local = localDataSource,
            mapper = mapper,
            database = database
        )

        networkMonitor = FakeNetworkMonitor(initialState = false)

        val useCase = SyncClientsUseCase(
            repository = repository,
            routeSyncRepository = FakeRouteSyncRepository(),
            networkMonitor = networkMonitor
        )

        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser

        coordinator = ClientSyncCoordinator(
            syncUseCase = useCase,
            networkMonitor = networkMonitor,
            firebaseAuth = mockAuth,
            applicationScope = testScope
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun offline_to_online_recovery_uploads_and_marks_synced_exactly_once() =
        testScope.runTest {

            val routeId = "route_1"

            database.routeDao().upsert(
                RouteEntity(
                    id = routeId,
                    name = "Ruta 1",
                    deliveryDay = 1,
                    syncStatus = SyncStatus.SYNCED,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            val clientId = "client_1"

            val client = ClientEntity(
                id = clientId,
                name = "Cliente Test",
                phone = "123456",
                address = "Calle Falsa 123",
                latitude = 0.0,
                longitude = 0.0,
                maxOrderAmountInCents = 1000L,
                isActive = true,
                isDeleted = false,
                routeId = routeId,
                syncStatus = SyncStatus.PENDING_CREATE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                createdBy = "tester",
                lastModifiedBy = "tester",
                lastSyncedAt = null
            )

            localDataSource.insert(client)

            // Verify initial state
            val pending = localDataSource.getPending(100)
            assertEquals(1, pending.size)
            assertEquals(SyncStatus.PENDING_CREATE, pending.first().syncStatus)
            assertEquals(1, localDataSource.getAll(100).size)

            // Notify local change while offline
            coordinator.notifyLocalChange()
            advanceUntilIdle()

            assertEquals(0, remoteDataSource.uploadCalls)
            assertEquals(
                SyncStatus.PENDING_CREATE,
                localDataSource.getById(clientId)?.syncStatus
            )

            // Simulate recovery: false -> true
            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertEquals(1, remoteDataSource.uploadCalls)

            val syncedClient = localDataSource.getById(clientId)
            assertNotNull(syncedClient)
            assertEquals(SyncStatus.SYNCED, syncedClient?.syncStatus)

            // Negative assertions
            assertNotEquals(SyncStatus.SYNCING, syncedClient?.syncStatus)
            assertNotEquals(SyncStatus.FAILED, syncedClient?.syncStatus)
            assertEquals(0, localDataSource.getPending(100).size)
            assertEquals(1, localDataSource.getAll(100).size)

            val remoteClients = remoteDataSource.fetchClients(100)
            assertEquals(1, remoteClients.size)
            assertNotNull(remoteDataSource.getClientById(clientId))

            // Idempotency
            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertEquals(1, remoteDataSource.uploadCalls)
            assertEquals(1, localDataSource.getAll(100).size)
        }
}

class FakeRouteSyncRepository : com.are.distribuidora.route.domain.repository.RouteSyncRepository {
    override suspend fun syncPending(limit: Int) {}
    override suspend fun pullRemote() {}
}
