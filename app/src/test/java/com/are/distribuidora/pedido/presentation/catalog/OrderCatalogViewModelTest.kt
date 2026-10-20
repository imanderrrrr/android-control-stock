package com.are.distribuidora.pedido.presentation.catalog

import com.are.distribuidora.domain.product.ObserveProductsUseCase
import com.are.distribuidora.workers.ProductSyncScheduler
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Frescura del stock entre teléfonos (2026-10-20): el catálogo de "Pedido nuevo" lee Room, y Room
 * solo recibía lo que vendieron los demás teléfonos cada 6 h o al abrir Inventario. Abrir el
 * catálogo debe pedir la bajada, igual que Inventario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrderCatalogViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `abrir el catalogo de Pedido nuevo pide bajar el catalogo de productos`() {
        val scheduler = mockk<ProductSyncScheduler>(relaxed = true)

        OrderCatalogViewModel(ObserveProductsUseCase(mockk(relaxed = true)), scheduler)

        verify(exactly = 1) { scheduler.scheduleOneTimeNow("ORDER_CATALOG_OPEN") }
    }
}
