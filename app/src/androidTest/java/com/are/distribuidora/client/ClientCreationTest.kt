package com.are.distribuidora.client

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.repository.ClientRepositoryImpl
import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.domain.validator.ClientValidator
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.client.fakes.FakeNetworkMonitor
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ClientCreationTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var local: ClientLocalDataSource
    private lateinit var fakeRemote: FakeClientRemote
    private lateinit var mapper: ClientRemoteMapper
    private lateinit var syncRepo: ClientSyncRepositoryImpl
    private lateinit var repository: ClientRepositoryImpl
    private lateinit var validator: ClientValidator
    private lateinit var coordinator: ClientSyncCoordinator

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DistribuidoraDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        local = ClientLocalDataSource(db.clientDao())
        fakeRemote = FakeClientRemote()
        mapper = ClientRemoteMapper()
        syncRepo = ClientSyncRepositoryImpl(
            remote = fakeRemote,
            local = local,
            mapper = mapper,
            database = db
        )
        validator = ClientValidator()

        val networkMonitor = FakeNetworkMonitor(initialState = true)
        val fakeRouteSyncRepo = mockk<com.are.distribuidora.route.domain.repository.RouteSyncRepository>(relaxed = true)
        val syncUseCase = SyncClientsUseCase(
            repository = syncRepo,
            routeSyncRepository = fakeRouteSyncRepo,
            networkMonitor = networkMonitor
        )

        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser

        coordinator = ClientSyncCoordinator(
            syncUseCase = syncUseCase,
            networkMonitor = networkMonitor,
            firebaseAuth = mockAuth,
            applicationScope = CoroutineScope(testDispatcher)
        )

        repository = ClientRepositoryImpl(
            local = local,
            remote = fakeRemote,
            sync = syncRepo,
            validator = validator,
            coordinator = coordinator
        )

        // FK requirement
        runBlocking {
            db.routeDao().upsert(
                RouteEntity(
                    id = "route-1",
                    name = "Ruta 1",
                    deliveryDay = 1,
                    syncStatus = SyncStatus.SYNCED,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // (resto del archivo igual al tuyo, no cambia nada)
}
