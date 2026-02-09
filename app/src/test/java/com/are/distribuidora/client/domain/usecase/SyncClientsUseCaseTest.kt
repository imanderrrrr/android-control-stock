package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.testClient
import com.are.distribuidora.core.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncClientsUseCaseTest {

    @Test
    fun `cuando hay internet sincroniza remoto y guarda local`() = runTest {
        val client = testClient(id = "1", name = "Cliente 1")
        val repo = FakeClientSyncRepository(remote = listOf(client))
        val routeRepo = FakeRouteSyncRepository()
        val network = FakeNetworkMonitor(online = true)
        val useCase = SyncClientsUseCase(
            repository = repo,
            routeSyncRepository = routeRepo,
            networkMonitor = network
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(repo.uploadCalled)
        assertTrue(repo.fetchCalled)
        assertTrue(repo.saveCalled)
        assertEquals(1, repo.fetchCalls)
        assertEquals(1, repo.saveCalls)
    }

    @Test
    fun `cuando no hay internet no llama remoto y retorna success`() = runTest {
        val repo = FakeClientSyncRepository(remote = emptyList())
        val routeRepo = FakeRouteSyncRepository()
        val network = FakeNetworkMonitor(online = false)
        val useCase = SyncClientsUseCase(
            repository = repo,
            routeSyncRepository = routeRepo,
            networkMonitor = network
        )

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, repo.fetchCalls)
        assertEquals(0, repo.saveCalls)
        assertTrue(!repo.uploadCalled)
        assertTrue(!repo.fetchCalled)
        assertTrue(!repo.saveCalled)
    }

    private class FakeNetworkMonitor(
        private val online: Boolean = true,
    ) : NetworkMonitor {
        override val isOnline: Flow<Boolean> = flowOf(online)
        override fun isOnline(): Boolean = online
    }

    private class FakeClientSyncRepository(
        private val remote: List<Client>,
        private val throwOnUpload: Boolean = false,
        private val throwOnFetch: Boolean = false,
        private val throwOnSave: Boolean = false,
    ) : ClientSyncRepository {

        var uploadCalled: Boolean = false
        var fetchCalled: Boolean = false
        var saveCalled: Boolean = false

        var fetchCalls: Int = 0
        var saveCalls: Int = 0

        override suspend fun uploadPendingClients() {
            uploadCalled = true
            if (throwOnUpload) throw RuntimeException("upload failed")
        }

        override suspend fun fetchRemoteClients(): List<Client> {
            fetchCalled = true
            fetchCalls += 1
            if (throwOnFetch) throw RuntimeException("fetch failed")
            return remote
        }

        override suspend fun saveLocalClients(clients: List<Client>) {
            saveCalled = true
            saveCalls += 1
            if (throwOnSave) throw RuntimeException("save failed")
        }
    }

    private class FakeRouteSyncRepository : com.are.distribuidora.route.domain.repository.RouteSyncRepository {
        override suspend fun syncPending(limit: Int) {}
        override suspend fun pullRemote() {}
    }
}
