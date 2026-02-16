package com.are.distribuidora.domain.sale

import androidx.paging.PagingData
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class SellProductUseCaseTest {

    private class FakeProductRepository(
        private var product: Product,
    ) : ProductRepository {

        var saveCalls = 0
            private set

        override fun getProductsStream(query: String?): Flow<PagingData<Product>> = emptyFlow()

        override suspend fun getById(id: ProductId): Product? {
            return if (id == product.id) product else null
        }

        override suspend fun save(product: Product) {
            saveCalls++
            this.product = product
        }

        fun current(): Product = product

        override suspend fun delete(id: String) {
            // No-op for this test
        }

        override fun getSyncStatuses(): Flow<Map<String, com.are.distribuidora.data.local.SyncStatus>> {
            return emptyFlow()
        }
    }

    private val p1Id = "PROD-001"
    private val p2Id = "PROD-002"
    private val defaultPrice = Money.of(BigDecimal("10.00"))

    @Test
    fun `vende y persiste stock correcto`() = runBlocking {
        val p1 = ProductId.of(p1Id)
        val initialProduct = Product(
            id = p1,
            name = "P1",
            stock = Quantity.of(10),
            price = defaultPrice,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val repo = FakeProductRepository(initialProduct)
        val useCase = SellProductUseCase(repo)

        val updated = useCase.execute(productId = p1Id, quantity = 3)

        assertEquals(Quantity.of(7), updated.stock)
        assertEquals(1, repo.saveCalls)
        assertEquals(Quantity.of(7), repo.current().stock)
    }

    @Test
    fun `falla si stock insuficiente y no persiste`() = runBlocking {
        val p2 = ProductId.of(p2Id)
        val initialProduct = Product(
            id = p2,
            name = "P2",
            stock = Quantity.of(2),
            price = defaultPrice,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val repo = FakeProductRepository(initialProduct)
        val useCase = SellProductUseCase(repo)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase.execute(productId = p2Id, quantity = 5) }
        }

        assertEquals(0, repo.saveCalls)
        assertEquals(Quantity.of(2), repo.current().stock)
    }
}
