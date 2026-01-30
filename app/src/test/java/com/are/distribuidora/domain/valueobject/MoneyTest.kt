package com.are.distribuidora.domain.valueobject

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `of creates Money with scale 2`() {
        val m = Money.of(BigDecimal("10.5"))
        assertEquals(BigDecimal("10.50"), m.amount)
    }

    @Test
    fun `of throws for negative`() {
        assertThrows(IllegalArgumentException::class.java) { Money.of(BigDecimal("-1")) }
    }

    @Test
    fun `plus adds money`() {
        val m1 = Money.of(BigDecimal("10.00"))
        val m2 = Money.of(BigDecimal("5.50"))
        assertEquals(BigDecimal("15.50"), (m1 + m2).amount)
    }

    @Test
    fun `times multiplies by integer`() {
        val m = Money.of(BigDecimal("10.00"))
        assertEquals(BigDecimal("30.00"), (m * 3).amount)
    }

    @Test
    fun `times throws for negative multiplier`() {
        assertThrows(IllegalArgumentException::class.java) { Money.of(BigDecimal("10")) * -1 }
    }
}
