package com.are.distribuidora.presentation.product

import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.domain.product.SaveProductUseCase
import com.are.distribuidora.workers.ImageUploadSyncScheduler
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddProductViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AddProductViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val saveProductUseCase = mockk<SaveProductUseCase>(relaxed = true)
        val pendingUploadDao = mockk<PendingUploadDao>(relaxed = true)
        val imageUploadSyncScheduler = mockk<ImageUploadSyncScheduler>(relaxed = true)
        viewModel = AddProductViewModel(saveProductUseCase, pendingUploadDao, imageUploadSyncScheduler)
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
        viewModel.onBarcodeChanged("111")
        assertEquals("111", viewModel.barcode.value)

        // When
        viewModel.onBarcodeChanged("222")

        // Then
        assertEquals("222", viewModel.barcode.value)
    }

    @Test
    fun `onBarcodeChanged with empty string updates state`() = runTest {
        viewModel.onBarcodeChanged("123")
        viewModel.onBarcodeChanged("")

        assertEquals("", viewModel.barcode.value)
    }
}

