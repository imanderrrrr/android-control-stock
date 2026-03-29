package com.are.distribuidora.presentation.product

import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.product.SaveProductUseCase
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.workers.ImageUploadSyncScheduler
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val saveProductUseCase = mockk<SaveProductUseCase>(relaxed = true)
        val pendingUploadDao = mockk<PendingUploadDao>(relaxed = true)
        val imageUploadSyncScheduler = mockk<ImageUploadSyncScheduler>(relaxed = true)
        viewModel = EditProductViewModel(productRepository, saveProductUseCase, pendingUploadDao, imageUploadSyncScheduler)
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
}

