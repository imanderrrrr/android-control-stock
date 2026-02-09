package com.are.distribuidora.client.sync

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.core.network.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ClientSyncCoordinatorTest {

    @Test
    fun `cuando network Flow emite true ejecuta syncUseCase`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        try {
            val network = FakeNetworkMonitor(initialOnline = false)
            val repo = FakeClientSyncRepository()
            val routeRepo = FakeRouteSyncRepository()
            val auth = createMockAuthWithUser()
            val useCase = SyncClientsUseCase(
                repository = repo,
                routeSyncRepository = routeRepo,
                networkMonitor = network
            )

            ClientSyncCoordinator(
                syncUseCase = useCase,
                networkMonitor = network,
                firebaseAuth = auth,
                applicationScope = scope,
            )

            network.emitOnline(true)
            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `notifyLocalChange online ejecuta syncUseCase`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        try {
            val network = FakeNetworkMonitor(initialOnline = true)
            val repo = FakeClientSyncRepository()
            val routeRepo = FakeRouteSyncRepository()
            val auth = createMockAuthWithUser()
            val useCase = SyncClientsUseCase(
                repository = repo,
                routeSyncRepository = routeRepo,
                networkMonitor = network
            )

            val coordinator = ClientSyncCoordinator(
                syncUseCase = useCase,
                networkMonitor = network,
                firebaseAuth = auth,
                applicationScope = scope,
            )

            coordinator.notifyLocalChange()
            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `notifyLocalChange offline no ejecuta syncUseCase`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        try {
            val network = FakeNetworkMonitor(initialOnline = false)
            val repo = FakeClientSyncRepository()
            val routeRepo = FakeRouteSyncRepository()
            val auth = createMockAuthWithUser()
            val useCase = SyncClientsUseCase(
                repository = repo,
                routeSyncRepository = routeRepo,
                networkMonitor = network
            )

            val coordinator = ClientSyncCoordinator(
                syncUseCase = useCase,
                networkMonitor = network,
                firebaseAuth = auth,
                applicationScope = scope,
            )

            coordinator.notifyLocalChange()
            advanceUntilIdle()

            assertEquals(0, repo.invocations.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `multiples notifyLocalChange rapidos no disparan ejecuciones paralelas`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val repo = FakeClientSyncRepository(blockFetchUntilReleased = true)
        try {
            val network = FakeNetworkMonitor(initialOnline = true)
            val routeRepo = FakeRouteSyncRepository()
            val auth = createMockAuthWithUser()
            val useCase = SyncClientsUseCase(
                repository = repo,
                routeSyncRepository = routeRepo,
                networkMonitor = network
            )

            val coordinator = ClientSyncCoordinator(
                syncUseCase = useCase,
                networkMonitor = network,
                firebaseAuth = auth,
                applicationScope = scope,
            )

            coordinator.notifyLocalChange()
            coordinator.notifyLocalChange()
            coordinator.notifyLocalChange()

            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
            assertEquals(1, repo.maxConcurrent.get())

            repo.release()
            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
        } finally {
            repo.release()
            scope.cancel()
        }
    }

    @Test
    fun `multiples emisiones online rapidas no disparan ejecuciones paralelas`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val repo = FakeClientSyncRepository(blockFetchUntilReleased = true)
        try {
            val network = FakeNetworkMonitor(initialOnline = false)
            val routeRepo = FakeRouteSyncRepository()
            val auth = createMockAuthWithUser()
            val useCase = SyncClientsUseCase(
                repository = repo,
                routeSyncRepository = routeRepo,
                networkMonitor = network
            )

            ClientSyncCoordinator(
                syncUseCase = useCase,
                networkMonitor = network,
                firebaseAuth = auth,
                applicationScope = scope,
            )

            network.emitOnline(true)
            network.emitOnline(true)
            network.emitOnline(true)

            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
            assertEquals(1, repo.maxConcurrent.get())

            repo.release()
            advanceUntilIdle()

            assertEquals(1, repo.invocations.get())
        } finally {
            repo.release()
            scope.cancel()
        }
    }

    private fun createMockAuthWithUser(): FirebaseAuth {
        val mockUser = mockk<FirebaseUser>()
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        every { mockAuth.currentUser } returns mockUser
        return mockAuth
    }

    private class FakeNetworkMonitor(initialOnline: Boolean) : NetworkMonitor {
        private val state = MutableStateFlow(initialOnline)
        override val isOnline = state.asStateFlow()
        override fun isOnline(): Boolean = state.value
        fun emitOnline(value: Boolean) {
            state.value = value
        }
    }

    private class FakeClientSyncRepository(
        private val blockFetchUntilReleased: Boolean = false,
    ) : ClientSyncRepository {
        val invocations = AtomicInteger(0)
        private val inFlight = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        private val gate = CompletableDeferred<Unit>()

        fun release() {
            if (blockFetchUntilReleased && !gate.isCompleted) {
                gate.complete(Unit)
            }
        }

        override suspend fun uploadPendingClients() {
            invocations.incrementAndGet()
            val now = inFlight.incrementAndGet()
            maxConcurrent.getAndUpdate { cur -> maxOf(cur, now) }
        }

        override suspend fun fetchRemoteClients(): List<Client> {
            if (blockFetchUntilReleased) {
                gate.await()
            }
            return emptyList()
        }

        override suspend fun saveLocalClients(clients: List<Client>) {
            inFlight.decrementAndGet()
        }
    }

    private class FakeRouteSyncRepository : com.are.distribuidora.route.domain.repository.RouteSyncRepository {
        override suspend fun syncPending(limit: Int) {}
        override suspend fun pullRemote() {}
    }
}
