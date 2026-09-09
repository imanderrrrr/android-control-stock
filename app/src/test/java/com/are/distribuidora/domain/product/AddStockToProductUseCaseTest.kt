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

    // ---- Tests del UseCase (4.1: "Agregar stock" = vale de ENTRADA) ----

    private fun build(repo: FakeProductRepositoryForStock): Pair<AddStockToProductUseCase, com.are.distribuidora.stockmovement.FakeStockMovementRepository> {
        val movements = com.are.distribuidora.stockmovement.FakeStockMovementRepository { _, delta -> repo.applyDelta(delta) }
        val voucher = com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase(
            movements, repo, com.are.distribuidora.stockmovement.FakeCurrentUser(),
            { com.are.distribuidora.screenaccess.domain.model.UserAccess.admin() },
        )
        return AddStockToProductUseCase(voucher) to movements
    }

    @Test
    fun `dado stock=10 y delta=5 registra ENTRADA COMPRA de 5 y el stock resultante es 15`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val (useCase, movements) = build(repo)

        val movement = useCase(product.id.value, 5)

        assertEquals(15, repo.currentStock)
        assertEquals(com.are.distribuidora.stockmovement.domain.model.MovementType.ENTRADA, movement.type)
        assertEquals(com.are.distribuidora.stockmovement.domain.model.MovementReason.COMPRA, movement.reason)
        assertEquals(5, movement.quantity)
        assertEquals(1, movements.recorded.size)
    }

    @Test
    fun `dado stock=0 y delta=1 el stock resultante es 1`() = runTest {
        val product = makeProduct(stock = 0)
        val repo = FakeProductRepositoryForStock(product)
        val (useCase, _) = build(repo)

        useCase(product.id.value, 1)

        assertEquals(1, repo.currentStock)
    }

    @Test
    fun `motivo y nota viajan en el vale`() = runTest {
        val product = makeProduct(stock = 0)
        val repo = FakeProductRepositoryForStock(product)
        val (useCase, movements) = build(repo)

        useCase(product.id.value, 4, com.are.distribuidora.stockmovement.domain.model.MovementReason.DEVOLUCION, "  cliente devolvió  ")

        val m = movements.recorded.single()
        assertEquals(com.are.distribuidora.stockmovement.domain.model.MovementReason.DEVOLUCION, m.reason)
        assertEquals("cliente devolvió", m.note)
    }

    @Test
    fun `delta=0 lanza IllegalArgumentException`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val (useCase, movements) = build(repo)

        try {
            useCase(product.id.value, 0)
            fail("Debería haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
        assertEquals(0, movements.recorded.size)
    }

    @Test
    fun `delta negativo lanza IllegalArgumentException`() = runTest {
        val product = makeProduct(stock = 10)
        val repo = FakeProductRepositoryForStock(product)
        val (useCase, _) = build(repo)

        try {
            useCase(product.id.value, -3)
            fail("Debería haber lanzado IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun `producto inexistente lanza NoSuchElementException`() = runTest {
        val repo = FakeProductRepositoryForStock(null)
        val (useCase, _) = build(repo)

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
    private var product: Product?,
) : ProductRepository {

    val currentStock: Int get() = product?.stock?.value ?: -1

    fun applyDelta(delta: Int) {
        product = product?.let { it.copy(stock = com.are.distribuidora.domain.valueobject.Quantity.of(it.stock.value + delta)) }
    }

    override suspend fun findByBarcode(barcode: String): Product? = product

    override suspend fun getById(id: ProductId): Product? = product?.takeIf { it.id == id }

    override fun getProductsStream(query: String?): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Product>> =
        kotlinx.coroutines.flow.emptyFlow()

    override fun observeById(id: ProductId): kotlinx.coroutines.flow.Flow<Product?> = kotlinx.coroutines.flow.emptyFlow()

    override suspend fun save(product: Product) { this.product = product }

    override suspend fun delete(id: String) {}

    override suspend fun countAll(): Int = if (product != null) 1 else 0

    override fun getSyncStatuses(): kotlinx.coroutines.flow.Flow<Map<String, com.are.distribuidora.domain.core.SyncState>> =
        kotlinx.coroutines.flow.emptyFlow()

    @Deprecated("4.1")
    override suspend fun incrementStock(productId: String, delta: Int) = error("no usar: el stock solo se mueve por vales")
}
