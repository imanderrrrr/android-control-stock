package com.are.distribuidora.stockmovement.presentation

import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * 4.1.4: cuando el caso de uso rechaza el sentido del vale (p. ej. SALIDA a un vendedor) la
 * pantalla recibe un evento FORBIDDEN explícito, no un "error genérico".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewVoucherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val product = Product(
        id = ProductId.of("prod-1"), name = "Prod", price = Money.of(BigDecimal("5.00")),
        stock = Quantity.of(0), createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `SALIDA rechazada por el caso de uso llega a la UI como FORBIDDEN`() = runTest {
        val useCase = mockk<CreateStockVoucherUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns Result.Error(Failure.Forbidden)
        val vm = NewVoucherViewModel(useCase, mockk(relaxed = true), mockk(relaxed = true))
        vm.selectProduct(product)
        vm.setType(MovementType.SALIDA)

        val event = async { vm.events.first() }
        advanceUntilIdle() // el colector ya está suscrito antes de enviar

        vm.submit("3", null)
        advanceUntilIdle()

        assertEquals(NewVoucherEvent.Error("FORBIDDEN"), event.await())
        coVerify(exactly = 1) { useCase("prod-1", MovementType.SALIDA, 3, any(), null) }
    }
}
