package com.are.distribuidora.domain.sale

import androidx.paging.PagingData
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.stockmovement.FakeCurrentUser
import com.are.distribuidora.stockmovement.FakeStockMovementRepository
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * 4.1: la venta directa desde Inventario ya no escribe el contador; registra un VALE DE SALIDA
 * (motivo OTRO). El stock local lo mueve el libro (aquí simulado por el fake) y puede quedar en
 * negativo: es información, no un error.
 */
class SellProductUseCaseTest {

    private class FakeProductRepository(private var product: Product) : ProductRepository {
        override fun getProductsStream(query: String?): Flow<PagingData<Product>> = emptyFlow()
        override suspend fun getById(id: ProductId): Product? = if (id == product.id) product else null
        override fun observeById(id: ProductId): Flow<Product?> = emptyFlow()
        override suspend fun save(product: Product) { this.product = product }
        override suspend fun delete(id: String) {}
        override fun getSyncStatuses(): Flow<Map<String, SyncState>> = emptyFlow()
        override suspend fun findByBarcode(barcode: String): Product? = null
        override suspend fun countAll(): Int = 0
        @Deprecated("4.1") override suspend fun incrementStock(productId: String, delta: Int) = error("no usar")
        fun applyDelta(delta: Int) { product = product.copy(stock = Quantity.of(product.stock.value + delta)) }
        fun current(): Product = product
    }

    private fun product(id: String, stock: Int) = Product(
        id = ProductId.of(id), name = "P-$id", stock = Quantity.of(stock),
        price = Money.of(BigDecimal("10.00")), createdAt = 1L, updatedAt = 1L,
    )

    private fun build(repo: FakeProductRepository): Pair<SellProductUseCase, FakeStockMovementRepository> {
        val movements = FakeStockMovementRepository { _, delta -> repo.applyDelta(delta) }
        val voucher = CreateStockVoucherUseCase(movements, repo, FakeCurrentUser())
        return SellProductUseCase(repo, voucher) to movements
    }

    @Test
    fun `vende registrando un vale de SALIDA y devuelve el stock resultante`() = runBlocking {
        val repo = FakeProductRepository(product("PROD-001", 10))
        val (useCase, movements) = build(repo)

        val updated = useCase.execute(productId = "PROD-001", quantity = 3)

        assertEquals(7, updated.stock.value)
        assertEquals(1, movements.recorded.size)
        val m = movements.recorded.single()
        assertEquals(MovementType.SALIDA, m.type)
        assertEquals(3, m.quantity)
        assertEquals(MovementReason.OTRO, m.reason)
        assertEquals(null, m.orderId)
        assertEquals("uid-test", m.createdBy)
    }

    @Test
    fun `stock insuficiente NO bloquea la venta y el stock queda en negativo`() = runBlocking {
        val repo = FakeProductRepository(product("PROD-002", 2))
        val (useCase, movements) = build(repo)

        val updated = useCase.execute(productId = "PROD-002", quantity = 5)

        assertEquals(-3, updated.stock.value)
        assertEquals(1, movements.recorded.size)
    }

    @Test
    fun `cantidad cero lanza y no registra movimiento`() = runBlocking {
        val repo = FakeProductRepository(product("PROD-003", 2))
        val (useCase, movements) = build(repo)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase.execute(productId = "PROD-003", quantity = 0) }
        }
        assertEquals(0, movements.recorded.size)
        assertEquals(2, repo.current().stock.value)
    }

    @Test
    fun `producto inexistente lanza y no registra movimiento`() = runBlocking {
        val repo = FakeProductRepository(product("PROD-004", 2))
        val (useCase, movements) = build(repo)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase.execute(productId = "NOPE", quantity = 1) }
        }
        assertEquals(0, movements.recorded.size)
    }
}
