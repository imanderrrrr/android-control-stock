package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class AddStockToProductUseCaseTest {

    private val now = System.currentTimeMillis()

    private fun makeProduct(id: String = "prod-1", stock: Int = 10): Product = Product(
        id = ProductId.of(id),
        name = "Producto Test",
        price = Money.of(BigDecimal("5.00")),
        stock = Quantity.of(stock),
        barcode = "1234567890",
        createdAt = now,
        updatedAt = now,
    )

    // ---- Tests del UseCase ----

    @Test
    fun `dado stock=10 y delta=5 el stock resultante es 15`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val useCase = AddStockToProductUseCase(repo)

        useCase(product.id.value, 5)

        assertEquals(15, repo.lastIncrementedStock)
    }

    @Test
    fun `dado stock=0 y delta=1 el stock resultante es 1`() = runTest {
        val product = makeProduct(stock = 0)
        val repo = FakeProductRepositoryForStock(product)
        val useCase = AddStockToProductUseCase(repo)

        useCase(product.id.value, 1)

        assertEquals(1, repo.lastIncrementedStock)
    }

    @Test
    fun `delta=0 lanza IllegalArgumentException`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val useCase = AddStockToProductUseCase(repo)

        try {
            useCase(product.id.value, 0)
            fail("Debería haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun `delta negativo lanza IllegalArgumentException`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val useCase = AddStockToProductUseCase(repo)

        try {
            useCase(product.id.value, -3)
            fail("Debería haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun `producto no encontrado lanza NoSuchElementException`() = runTest {
        val repo = FakeProductRepositoryForStock(null)
        val useCase = AddStockToProductUseCase(repo)

        try {
            useCase("id-inexistente", 5)
            fail("Debería haber lanzado NoSuchElementException")
        } catch (e: NoSuchElementException) {
            // esperado
        }
    }

    // ---- Tests de FindProductByBarcodeUseCase ----

    @Test
    fun `findByBarcode retorna producto cuando existe`() = runTest {
        val product = makeProduct()
        val repo = FakeProductRepositoryForStock(product)
        val useCase = FindProductByBarcodeUseCase(repo)

        val result = useCase("1234567890")

        assertNotNull(result)
        assertEquals(product.id, result!!.id)
    }

    @Test
    fun `findByBarcode retorna null cuando no existe`() = runTest {
        val repo = FakeProductRepositoryForStock(null)
        val useCase = FindProductByBarcodeUseCase(repo)

        val result = useCase("barcode-no-existente")

        assertNull(result)
    }

    @Test
    fun `findByBarcode con barcode vacío lanza IllegalArgumentException`() = runTest {
        val repo = FakeProductRepositoryForStock(null)
        val useCase = FindProductByBarcodeUseCase(repo)

        try {
            useCase("  ")
            fail("Debería haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }
}

/** Fake repository para tests de stock */
private class FakeProductRepositoryForStock(
    private val productToReturn: Product?,
) : ProductRepository {

    var lastIncrementedStock: Int = -1
        private set

    override suspend fun findByBarcode(barcode: String): Product? = productToReturn

    override suspend fun incrementStock(productId: String, delta: Int) {
        val current = productToReturn?.stock?.value ?: 0
        lastIncrementedStock = current + delta
    }

    override suspend fun getById(id: com.are.distribuidora.domain.valueobject.ProductId): Product? =
        if (productToReturn?.id == id) productToReturn else null

    // Implementaciones vacías requeridas por la interfaz
    override fun getProductsStream(query: String?) = throw UnsupportedOperationException()
    override fun observeById(id: com.are.distribuidora.domain.valueobject.ProductId) = throw UnsupportedOperationException()
    override suspend fun save(product: Product) = throw UnsupportedOperationException()
    override suspend fun delete(id: String) = throw UnsupportedOperationException()
    override fun getSyncStatuses() = throw UnsupportedOperationException()
}

