package com.are.distribuidora.presentation.product

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.product.SaveProductUseCase
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import com.are.distribuidora.workers.ImageUploadSyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class EditProductViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EditProductViewModel
    private lateinit var productRepository: ProductRepository
    private lateinit var saveProductUseCase: SaveProductUseCase
    private lateinit var createStockVoucher: CreateStockVoucherUseCase

    private val testProduct = Product(
        id = ProductId.of("prod-1"),
        name = "Test Product",
        description = "Test Desc",
        category = "Cat",
        price = Money.of(BigDecimal("10.00")),
        imageUrl = "https://example.com/img.jpg",
        barcode = "OLD_BARCODE",
        stock = Quantity.of(5),
        isActive = true,
        isDeleted = false,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        productRepository = mockk(relaxed = true)
        saveProductUseCase = mockk(relaxed = true)
        val pendingUploadDao = mockk<PendingUploadDao>(relaxed = true)
        val imageUploadSyncScheduler = mockk<ImageUploadSyncScheduler>(relaxed = true)
        createStockVoucher = mockk()
        coEvery { createStockVoucher(any(), any(), any(), any(), any()) } returns Result.Success(mockk())
        viewModel = EditProductViewModel(productRepository, saveProductUseCase, pendingUploadDao, imageUploadSyncScheduler, createStockVoucher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onBarcodeChanged updates barcode state`() = runTest {
        // Initially null
        assertNull(viewModel.barcode.value)

        // When
        viewModel.onBarcodeChanged("123456789")

        // Then
        assertEquals("123456789", viewModel.barcode.value)
    }

    @Test
    fun `onBarcodeChanged replaces previous barcode`() = runTest {
        // Given
        viewModel.onBarcodeChanged("OLD")
        assertEquals("OLD", viewModel.barcode.value)

        // When
        viewModel.onBarcodeChanged("NEW")

        // Then
        assertEquals("NEW", viewModel.barcode.value)
    }

    @Test
    fun `load product then onBarcodeChanged keeps new barcode`() = runTest {
        // Given: load an existing product
        coEvery { productRepository.getById(ProductId.of("prod-1")) } returns testProduct
        viewModel.load("prod-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is EditProductViewModel.EditProductUiState.Success)
        assertEquals("OLD_BARCODE", (state as EditProductViewModel.EditProductUiState.Success).product.barcode)

        // When: scan a new barcode
        viewModel.onBarcodeChanged("NEW_SCANNED_BARCODE")

        // Then: barcode state updated
        assertEquals("NEW_SCANNED_BARCODE", viewModel.barcode.value)
    }

    @Test
    fun `onBarcodeChanged with empty string updates state`() = runTest {
        viewModel.onBarcodeChanged("123")
        viewModel.onBarcodeChanged("")

        assertEquals("", viewModel.barcode.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4.1: el stock puede ser NEGATIVO (lo mueven los pedidos, no el formulario).
    // Fallo de producción 2026-09-17: con 179 productos en negativo, "Editar producto"
    // pre-llenaba el stock con "-20" y al guardar un precio nuevo respondía
    // "El stock debe ser un número entero mayor o igual a 0". El admin no podía
    // cambiar precios de nada que se hubiera vendido.
    // ─────────────────────────────────────────────────────────────────────────

    private fun productWithStock(stock: Int): Product = testProduct.copy(stock = Quantity.of(stock))

    private fun TestScope.loadProduct(product: Product) {
        coEvery { productRepository.getById(ProductId.of("prod-1")) } returns product
        viewModel.load("prod-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is EditProductViewModel.EditProductUiState.Success)
    }

    private fun saveForm(priceText: String, stockText: String) {
        viewModel.save(
            name = "Test Product",
            description = "Test Desc",
            category = "Cat",
            priceText = priceText,
            stockText = stockText,
            barcode = "OLD_BARCODE",
            isActive = true,
        )
    }

    @Test
    fun `save with a negative stock left untouched persists the new price and creates no voucher`() = runTest {
        loadProduct(productWithStock(-20))
        val event = async { viewModel.events.first() }

        // El formulario viene pre-llenado con "-20"; el usuario solo toca el precio.
        saveForm(priceText = "13.75", stockText = "-20")
        advanceUntilIdle()

        assertEquals(EditProductViewModel.Event.Success, event.await())
        val saved = slot<Product>()
        coVerify(exactly = 1) { saveProductUseCase(capture(saved)) }
        assertEquals(BigDecimal("13.75"), saved.captured.price.amount)
        assertEquals(-20, saved.captured.stock.value)
        coVerify(exactly = 0) { createStockVoucher(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `save with a negative stock corrected to zero records an AJUSTE voucher for the delta`() = runTest {
        loadProduct(productWithStock(-20))
        val event = async { viewModel.events.first() }

        saveForm(priceText = "10.00", stockText = "0")
        advanceUntilIdle()

        assertEquals(EditProductViewModel.Event.Success, event.await())
        coVerify(exactly = 1) { saveProductUseCase(any()) }
        coVerify(exactly = 1) {
            createStockVoucher("prod-1", MovementType.ENTRADA, 20, MovementReason.AJUSTE, any())
        }
    }

    @Test
    fun `save rejects a stock that is not an integer`() = runTest {
        loadProduct(productWithStock(5))
        val event = async { viewModel.events.first() }

        saveForm(priceText = "10.00", stockText = "abc")
        advanceUntilIdle()

        assertTrue(event.await() is EditProductViewModel.Event.Error)
        coVerify(exactly = 0) { saveProductUseCase(any()) }
        coVerify(exactly = 0) { createStockVoucher(any(), any(), any(), any(), any()) }
    }
}
