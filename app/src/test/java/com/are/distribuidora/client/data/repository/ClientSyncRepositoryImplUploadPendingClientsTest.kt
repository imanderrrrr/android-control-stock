package com.are.distribuidora.client.data.repository

import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.data.local.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSyncRepositoryImplUploadPendingClientsTest {

    @Test
    fun `upload failure - marca FAILED`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_CREATE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = setOf("1"),
            deleteFailsFor = emptySet(),
            remoteSnapshots = mapOf("1" to testDto(id = "1", updatedAt = 111L)),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        assertEquals(SyncStatus.FAILED, local.getById("1")!!.syncStatus)
    }

    @Test
    fun `delete failure - marca FAILED`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_DELETE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = setOf("1"),
            remoteSnapshots = emptyMap(),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        assertEquals(SyncStatus.FAILED, local.getById("1")!!.syncStatus)
    }

    @Test
    fun `successful upload - marca SYNCED`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = emptySet(),
            remoteSnapshots = mapOf("1" to testDto(id = "1", updatedAt = 999L)),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        val stored = local.getById("1")!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals(999L, stored.updatedAt)
        assertTrue(stored.lastSyncedAt != null && stored.lastSyncedAt > 0L)
    }

    @Test
    fun `successful delete - elimina local`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_DELETE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = emptySet(),
            remoteSnapshots = emptyMap(),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        assertNull(local.getById("1"))
    }

    @Test
    fun `idempotente - correr dos veces no duplica uploads`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_CREATE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = emptySet(),
            remoteSnapshots = mapOf("1" to testDto(id = "1", updatedAt = 10L)),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()
        repo.uploadPendingClients()

        assertEquals(1, remote.uploadCallsForId("1"))
        assertEquals(SyncStatus.SYNCED, local.getById("1")!!.syncStatus)
    }

    @Test
    fun `si uno falla otros siguen`() = runTest {
        val e1 = testEntity(id = "1", syncStatus = SyncStatus.PENDING_CREATE)
        val e2 = testEntity(id = "2", syncStatus = SyncStatus.PENDING_CREATE)
        val dao = FakeClientDao(listOf(e1, e2))
        val local = ClientLocalDataSource(dao)
        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = setOf("1"),
            deleteFailsFor = emptySet(),
            remoteSnapshots = mapOf("2" to testDto(id = "2", updatedAt = 20L)),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        assertEquals(SyncStatus.FAILED, local.getById("1")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, local.getById("2")!!.syncStatus)
        assertEquals(1, remote.uploadCallsForId("1"))
        assertEquals(1, remote.uploadCallsForId("2"))
    }

    @Test
    fun `snapshot post-upload no disponible - no marca FAILED, queda reintentable y luego SYNCED`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_CREATE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)

        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = emptySet(),
            remoteSnapshots = mapOf(
                // 1er ciclo: snapshot todavía no está (serverTimestamp no materializado)
                "1" to null,
            ),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        val afterFirst = local.getById("1")!!
        assertEquals(SyncStatus.PENDING_CREATE, afterFirst.syncStatus)
        assertEquals(1, remote.uploadCallsForId("1"))

        // 2do ciclo: ahora sí aparece snapshot con updatedAt
        remote.setSnapshot("1", testDto(id = "1", updatedAt = 123L))
        repo.uploadPendingClients()

        val afterSecond = local.getById("1")!!
        assertEquals(SyncStatus.SYNCED, afterSecond.syncStatus)
        assertEquals(123L, afterSecond.updatedAt)
        assertEquals(2, remote.uploadCallsForId("1"))
    }

    @Test
    fun `snapshot post-upload lanza excepcion - no marca FAILED, queda reintentable`() = runTest {
        val entity = testEntity(id = "1", syncStatus = SyncStatus.PENDING_CREATE)
        val dao = FakeClientDao(listOf(entity))
        val local = ClientLocalDataSource(dao)

        val remote = FakeClientRemoteDataSource(
            uploadFailsFor = emptySet(),
            deleteFailsFor = emptySet(),
            remoteSnapshots = emptyMap(),
            throwOnGetByIdFor = setOf("1"),
        )
        val repo = newRepo(remote = remote, local = local)

        repo.uploadPendingClients()

        val stored = local.getById("1")!!
        assertEquals(SyncStatus.PENDING_CREATE, stored.syncStatus)
        assertEquals(1, remote.uploadCallsForId("1"))
    }

    private fun newRepo(remote: ClientRemoteDataSource, local: ClientLocalDataSource): ClientSyncRepositoryImpl {
        val mockDatabase = io.mockk.mockk<com.are.distribuidora.data.local.DistribuidoraDatabase>(relaxed = true) {
            io.mockk.coEvery { runInTransaction(any<suspend () -> Any>()) } coAnswers {
                val block = firstArg<suspend () -> Any>()
                block()
            }
        }

        return ClientSyncRepositoryImpl(
            remote = remote,
            local = local,
            mapper = ClientRemoteMapper(),
            database = mockDatabase
        )
    }

    private fun testEntity(
        id: String,
        syncStatus: SyncStatus,
        updatedAt: Long = 1L,
        createdAt: Long = 1L,
    ): ClientEntity =
        ClientEntity(
            id = id,
            name = "Client $id",
            phone = null,
            address = null,
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = null,
            isActive = true,
            routeId = "route-1",
            syncStatus = syncStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = "test",
            lastModifiedBy = "test",
            lastSyncedAt = null,
        )

    private fun testDto(id: String, updatedAt: Long): ClientDto =
        ClientDto(
            id = id,
            name = "Client $id",
            phone = null,
            address = null,
            latitude = null,
            longitude = null,
            maxOrderAmountInCents = null,
            isActive = true,
            auditCreatedBy = "test",
            auditLastModifiedBy = "test",
            createdAt = 1L,
            updatedAt = updatedAt,
            routeId = "route-1",
        )

    private class FakeClientDao(seed: List<ClientEntity>) : com.are.distribuidora.client.data.local.dao.ClientDao {
        private val store: MutableMap<String, ClientEntity> = seed.associateBy { it.id }.toMutableMap()

        override suspend fun insert(entity: ClientEntity): Long = 0
        override suspend fun update(entity: ClientEntity) {}
        override suspend fun getAll(limit: Int): List<ClientEntity> = emptyList()

        override suspend fun getByRouteId(routeId: String, limit: Int): List<ClientEntity> =
            store.values.filter { it.routeId == routeId }.take(limit)

        override fun observeByRouteId(routeId: String, limit: Int): kotlinx.coroutines.flow.Flow<List<ClientEntity>> =
            kotlinx.coroutines.flow.flowOf(store.values.filter { it.routeId == routeId && !it.isDeleted && it.syncStatus != SyncStatus.PENDING_DELETE }.take(limit))

        override suspend fun updateRoute(clientId: String, routeId: String) {}
        override suspend fun countByRoute(routeId: String): Int = 0

        override suspend fun getById(id: String): ClientEntity? = store[id]

        override suspend fun getPending(statuses: List<SyncStatus>, limit: Int): List<ClientEntity> =
            store.values.filter { it.syncStatus in statuses }.take(limit)

        override suspend fun getMaxUpdatedAt(): Long? =
            store.values.maxOfOrNull { it.updatedAt }

        override suspend fun markAsPendingDelete(id: String, status: SyncStatus) {}
        override suspend fun delete(id: String) {}

        override suspend fun markSyncingInternal(id: String, syncingStatus: SyncStatus) {
            val cur = store[id] ?: return
            store[id] = cur.copy(syncStatus = syncingStatus)
        }

        override suspend fun markFailedInternal(id: String, failedStatus: SyncStatus) {
            val cur = store[id] ?: return
            store[id] = cur.copy(syncStatus = failedStatus)
        }

        override suspend fun markSyncedInternal(
            id: String,
            updatedAt: Long,
            lastSyncedAt: Long,
            syncedStatus: SyncStatus,
        ) {
            val cur = store[id] ?: return
            store[id] = cur.copy(syncStatus = syncedStatus, updatedAt = updatedAt, lastSyncedAt = lastSyncedAt)
        }

        override suspend fun deleteInternal(id: String) {
            store.remove(id)
        }

        override suspend fun markSyncing(id: String) {
            markSyncingInternal(id = id, syncingStatus = SyncStatus.SYNCING)
        }

        override suspend fun markFailed(id: String) {
            markFailedInternal(id = id, failedStatus = SyncStatus.FAILED)
        }

        override suspend fun markSynced(id: String, updatedAt: Long, lastSyncedAt: Long) {
            markSyncedInternal(
                id = id,
                updatedAt = updatedAt,
                lastSyncedAt = lastSyncedAt,
                syncedStatus = SyncStatus.SYNCED,
            )
        }

        override suspend fun deleteInsideTransaction(id: String) {
            deleteInternal(id = id)
        }

        override suspend fun markPendingInternal(id: String, pendingStatus: SyncStatus) {
            val cur = store[id] ?: return
            store[id] = cur.copy(syncStatus = pendingStatus)
        }
    }

    private class FakeClientRemoteDataSource(
        private val uploadFailsFor: Set<String>,
        private val deleteFailsFor: Set<String>,
        remoteSnapshots: Map<String, ClientDto?>,
        private val throwOnGetByIdFor: Set<String> = emptySet(),
    ) : ClientRemoteDataSource {

        private val uploadCalls: MutableMap<String, Int> = mutableMapOf()
        private val snapshots: MutableMap<String, ClientDto?> = remoteSnapshots.toMutableMap()

        override suspend fun uploadClient(client: ClientDto) {
            uploadCalls[client.id] = (uploadCalls[client.id] ?: 0) + 1
            if (uploadFailsFor.contains(client.id)) throw RuntimeException("upload failed")
        }

        override suspend fun softDeleteClient(id: String) {
            if (deleteFailsFor.contains(id)) throw RuntimeException("delete failed")
            // Simular el comportamiento de Firestore: actualizar el snapshot con isDeleted=true
            val existing = snapshots[id]
            if (existing != null) {
                snapshots[id] = existing.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
            }
        }

        override suspend fun fetchClients(limit: Int): List<ClientDto> = emptyList()

        override suspend fun fetchClientsAfter(timestamp: Long): List<ClientDto> = emptyList()

        override suspend fun getClientById(id: String): ClientDto? {
            if (throwOnGetByIdFor.contains(id)) {
                throw RuntimeException("timeout")
            }
            return snapshots[id]
        }

        fun uploadCallsForId(id: String): Int = uploadCalls[id] ?: 0

        fun setSnapshot(id: String, dto: ClientDto?) {
            snapshots[id] = dto
        }
    }
}
