package com.are.distribuidora.client

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.repository.ClientRepositoryImpl
import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.client.domain.validator.ClientValidator
import com.are.distribuidora.client.fakes.FakeNetworkMonitor
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ClientDomainValidationTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var local: ClientLocalDataSource
    private lateinit var fakeRemote: FakeClientRemote
    private lateinit var mapper: ClientRemoteMapper
    private lateinit var syncRepo: ClientSyncRepository
    private lateinit var repository: ClientRepository
    private lateinit var validator: ClientValidator
    private lateinit var coordinator: ClientSyncCoordinator

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
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
            applicationScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )

        repository = ClientRepositoryImpl(
            local = local,
            remote = fakeRemote,
            sync = syncRepo,
            validator = validator,
            coordinator = coordinator
        )

        // Insertar Route válida para cumplir FK
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

    @Test
    fun test4_maxOrderAmountNegativo_debeFallarEnDominio() = runTest {
        val client = Client(
            id = UUID.randomUUID().toString(),
            name = "Cliente Invalido",
            phone = "123",
            address = "Calle",
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = -100L, // INVALID
            isActive = true,
            isDeleted = false,
            routeId = "route-1",
            syncState = SyncState.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            createdBy = "tester",
            lastModifiedBy = "tester"
        )

        val result = repository.create(client)

        assertTrue(result is Result.Error)
        assertNull(local.getById(client.id))
        assertEquals(0, fakeRemote.uploadCalls)
    }

    @Test
    fun test5_datosObligatoriosFaltantes_debeFallarEnDominio() = runTest {

        val clientEmptyName = Client(
            id = UUID.randomUUID().toString(),
            name = "", // INVALID
            phone = "123",
            address = "Calle",
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = 1000L,
            isActive = true,
            isDeleted = false,
            routeId = "route-1",
            syncState = SyncState.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            createdBy = "tester",
            lastModifiedBy = "tester"
        )

        val resultName = repository.create(clientEmptyName)

        assertTrue(resultName is Result.Error)
        assertNull(local.getById(clientEmptyName.id))
        assertEquals(0, fakeRemote.uploadCalls)

        val clientEmptyRoute = clientEmptyName.copy(
            id = UUID.randomUUID().toString(),
            name = "Valid Name",
            routeId = "" // INVALID
        )

        val resultRoute = repository.create(clientEmptyRoute)

        assertTrue(resultRoute is Result.Error)
        assertNull(local.getById(clientEmptyRoute.id))
    }

    // --- Fake Remote ---
    // (removido: ahora se usa el alias FakeClientRemote -> FakeClientRemoteDataSource en androidTest)
}
