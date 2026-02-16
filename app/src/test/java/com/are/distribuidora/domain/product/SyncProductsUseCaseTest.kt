package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProductsUseCaseTest {

    @Test
    fun `invoke delega en el repositorio cuando hay conexión`() = runTest {
        val repo = FakeProductSyncRepository()
        val connectivityChecker = FakeConnectivityChecker(isOnline = true)
        val logger = FakeLogger()
        val useCase = SyncProductsUseCase(repo, connectivityChecker, logger)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, repo.fetchCalls)
        assertEquals(1, repo.saveCalls)
        assertEquals(1, repo.uploadCalls)
    }

    @Test
    fun `invoke no sincroniza cuando no hay conexión`() = runTest {
        val repo = FakeProductSyncRepository()
        val connectivityChecker = FakeConnectivityChecker(isOnline = false)
        val logger = FakeLogger()
        val useCase = SyncProductsUseCase(repo, connectivityChecker, logger)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(0, repo.fetchCalls)
        assertEquals(0, repo.saveCalls)
        assertEquals(0, repo.uploadCalls)
    }

    private class FakeProductSyncRepository : ProductSyncRepository {
        var fetchCalls: Int = 0
        var saveCalls: Int = 0
        var uploadCalls: Int = 0

        override suspend fun fetchRemoteProducts(): List<Product> {
            fetchCalls++
            return emptyList()
        }

        override suspend fun saveLocalProducts(products: List<Product>) {
            saveCalls++
        }

        override suspend fun syncDownstream() {
            // No-op for this unit test
        }

        override suspend fun uploadPendingProducts() {
            uploadCalls++
        }
    }

    private class FakeConnectivityChecker(private val isOnline: Boolean) : ConnectivityChecker {
        override suspend fun isOnline(): Boolean = isOnline
    }

    private class FakeLogger : Logger {
        override fun d(tag: String, message: String) {}
        override fun e(tag: String, message: String, throwable: Throwable?) {}
    }
}
