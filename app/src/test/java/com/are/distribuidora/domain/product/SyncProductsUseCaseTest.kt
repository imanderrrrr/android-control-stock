package com.are.distribuidora.domain.product

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProductsUseCaseTest {

    @Test
    fun `invoke delega en el repositorio exactamente una vez`() = runTest {
        val repo = FakeProductSyncRepository()
        val useCase = SyncProductsUseCase(repo)

        useCase()

        assertEquals(1, repo.calls)
    }

    private class FakeProductSyncRepository : ProductSyncRepository {
        var calls: Int = 0
        override suspend fun syncProductsRemoteToLocal() {
            calls++
        }
    }
}
