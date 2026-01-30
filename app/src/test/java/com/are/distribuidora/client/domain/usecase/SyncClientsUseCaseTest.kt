package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.core.network.NetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncClientsUseCaseTest {

    @Test
    fun `cuando hay internet sincroniza remoto y guarda local`() = runTest {
        val client = Client(
            id = "1",
            name = "Cliente 1",
            address = null,
            createdAt = 0L,
        )
        val repo = FakeClientSyncRepository(remote = listOf(client))
        val network = FakeNetworkMonitor(online = true)
        val useCase = SyncClientsUseCase(repository = repo, networkMonitor = network)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.fetchCalls)
        assertEquals(1, repo.saveCalls)
    }

    @Test
    fun `cuando no hay internet no llama remoto y retorna success`() = runTest {
        val repo = FakeClientSyncRepository(remote = emptyList())
        val network = FakeNetworkMonitor(online = false)
        val useCase = SyncClientsUseCase(repository = repo, networkMonitor = network)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, repo.fetchCalls)
        assertEquals(0, repo.saveCalls)
    }

    private class FakeNetworkMonitor(
        private val online: Boolean,
    ) : NetworkMonitor {
        override fun isOnline(): Boolean = online
    }

    private class FakeClientSyncRepository(
        private val remote: List<Client>,
        private val throwOnFetch: Boolean = false,
        private val throwOnSave: Boolean = false,
    ) : ClientSyncRepository {
        var fetchCalls: Int = 0
        var saveCalls: Int = 0

        override suspend fun fetchRemoteClients(): List<Client> {
            fetchCalls += 1
            if (throwOnFetch) throw RuntimeException("fetch failed")
            return remote
        }

        override suspend fun saveLocalClients(clients: List<Client>) {
            saveCalls += 1
            if (throwOnSave) throw RuntimeException("save failed")
        }
    }
}
