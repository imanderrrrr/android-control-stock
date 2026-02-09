package com.are.distribuidora.client

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.fakes.FakeClientRemoteDataSource
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.route.data.local.entity.RouteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0: El downsync no debe crashear ni romper todo el lote si remoto trae datos inválidos.
 * - Si routeId es null/blank => se ignora.
 * - Si routeId no existe localmente => se ignora (por FK), pero NO debe abortar la sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ClientDownsyncInvalidRemoteDataTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var local: ClientLocalDataSource
    private lateinit var remote: FakeClientRemoteDataSource
    private lateinit var syncRepo: ClientSyncRepositoryImpl

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        local = ClientLocalDataSource(db.clientDao())
        remote = FakeClientRemoteDataSource()
        syncRepo = ClientSyncRepositoryImpl(
            remote = remote,
            local = local,
            mapper = ClientRemoteMapper()
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun downsync_ignores_invalid_routeId_and_keeps_processing_valid_clients() = scope.runTest {
        // Solo existe esta ruta local
        val validRouteId = "route-ok"
        db.routeDao().upsert(
            RouteEntity(
                id = validRouteId,
                name = "Ruta OK",
                deliveryDay = 1,
                syncStatus = SyncStatus.SYNCED,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Remoto: 1 válido, 1 inválido (routeId null), 1 con FK rota (route inexistente)
        remote.store["c-valid"] = ClientDto(
            id = "c-valid",
            name = "Cliente Válido",
            phone = null,
            address = null,
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = null,
            isActive = true,
            auditCreatedBy = "remote",
            auditLastModifiedBy = "remote",
            createdAt = 10L,
            updatedAt = 20L,
            routeId = validRouteId,
        )

        remote.store["c-null-route"] = ClientDto(
            id = "c-null-route",
            name = "Cliente Sin Ruta",
            phone = null,
            address = null,
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = null,
            isActive = true,
            auditCreatedBy = "remote",
            auditLastModifiedBy = "remote",
            createdAt = 10L,
            updatedAt = 20L,
            routeId = null,
        )

        remote.store["c-bad-fk"] = ClientDto(
            id = "c-bad-fk",
            name = "Cliente FK Rota",
            phone = null,
            address = null,
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = null,
            isActive = true,
            auditCreatedBy = "remote",
            auditLastModifiedBy = "remote",
            createdAt = 10L,
            updatedAt = 20L,
            routeId = "route-missing",
        )

        val remoteClients = syncRepo.fetchRemoteClients()
        // No debe lanzar excepción
        syncRepo.saveLocalClients(remoteClients)

        val allLocal = local.getAll(50)
        assertEquals(1, allLocal.size)
        assertEquals("c-valid", allLocal.first().id)
        assertEquals(validRouteId, allLocal.first().routeId)
        assertEquals(SyncStatus.SYNCED, allLocal.first().syncStatus)
    }
}
