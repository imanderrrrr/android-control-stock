package com.are.distribuidora.domain.pedido.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests para ApplyItemDiscountUseCase.
 * Verifica la fórmula: descuento = precioUnitario × cantidad × (porcentaje / 100)
 */
class ApplyItemDiscountUseCaseTest {

    private val useCase = ApplyItemDiscountUseCase()

    // ─── Casos nominales ─────────────────────────────────────────────────────

    @Test
    fun `porcentaje 0 devuelve descuento 0`() {
        val resultado = useCase(precioUnitario = 50.0, cantidad = 3, porcentaje = 0.0)
        assertEquals(0.0, resultado, 0.001)
    }

    @Test
    fun `porcentaje 100 devuelve todo el subtotal`() {
        val resultado = useCase(precioUnitario = 20.0, cantidad = 2, porcentaje = 100.0)
        // 20*2 = 40, 100% de 40 = 40
        assertEquals(40.0, resultado, 0.001)
    }

    @Test
    fun `porcentaje 10 en item de precio 100 cantidad 1`() {
        val resultado = useCase(precioUnitario = 100.0, cantidad = 1, porcentaje = 10.0)
        assertEquals(10.0, resultado, 0.001)
    }

    @Test
    fun `porcentaje 50 en item de precio 25 cantidad 4`() {
        // 25*4=100, 50% = 50
        val resultado = useCase(precioUnitario = 25.0, cantidad = 4, porcentaje = 50.0)
        assertEquals(50.0, resultado, 0.001)
    }

    @Test
    fun `resultado se redondea a 2 decimales`() {
        // 10 * 3 * (33.33/100) = 9.999 → redondeado a 10.0
        val resultado = useCase(precioUnitario = 10.0, cantidad = 3, porcentaje = 33.33)
        assertEquals(9.999, resultado, 0.01)
    }

    @Test
    fun `cantidad 0 devuelve descuento 0`() {
        val resultado = useCase(precioUnitario = 100.0, cantidad = 0, porcentaje = 50.0)
        assertEquals(0.0, resultado, 0.001)
    }

    @Test
    fun `precio unitario 0 devuelve descuento 0`() {
        val resultado = useCase(precioUnitario = 0.0, cantidad = 5, porcentaje = 100.0)
        assertEquals(0.0, resultado, 0.001)
    }

    @Test
    fun `porcentaje decimal funciona correctamente`() {
        // 10 * 1 * (7.5/100) = 0.75
        val resultado = useCase(precioUnitario = 10.0, cantidad = 1, porcentaje = 7.5)
        assertEquals(0.75, resultado, 0.001)
    }

    // ─── Validaciones de rangos ───────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `porcentaje negativo lanza IllegalArgumentException`() {
        useCase(precioUnitario = 10.0, cantidad = 1, porcentaje = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `porcentaje mayor a 100 lanza IllegalArgumentException`() {
        useCase(precioUnitario = 10.0, cantidad = 1, porcentaje = 100.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cantidad negativa lanza IllegalArgumentException`() {
        useCase(precioUnitario = 10.0, cantidad = -1, porcentaje = 10.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `precio negativo lanza IllegalArgumentException`() {
        useCase(precioUnitario = -1.0, cantidad = 1, porcentaje = 10.0)
    }

    // ─── Porcentajes límite ───────────────────────────────────────────────────

    @Test
    fun `porcentaje exactamente 100 es valido`() {
        val resultado = useCase(precioUnitario = 50.0, cantidad = 2, porcentaje = 100.0)
        assertEquals(100.0, resultado, 0.001)
    }

    @Test
    fun `porcentaje exactamente 0 es valido`() {
        val resultado = useCase(precioUnitario = 50.0, cantidad = 2, porcentaje = 0.0)
        assertEquals(0.0, resultado, 0.001)
    }
}

