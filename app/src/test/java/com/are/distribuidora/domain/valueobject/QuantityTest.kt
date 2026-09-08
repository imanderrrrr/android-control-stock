package com.are.distribuidora.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantityTest {

    @Test
    fun `of creates Quantity for positive values`() {
        val q = Quantity.of(10)
        assertEquals(10, q.value)
    }

    @Test
    fun `of allows negative since 4_1 (stock negativo es informacion)`() {
        val q = Quantity.of(-1)
        assertEquals(-1, q.value)
        assertTrue(q.isNegative())
    }

    @Test
    fun `of allows zero`() {
        val q = Quantity.of(0)
        assertEquals(0, q.value)
        assertTrue(q.isZero())
    }

    @Test
    fun `zero creates zero Quantity`() {
        val q = Quantity.zero()
        assertEquals(0, q.value)
        assertTrue(q.isZero())
    }

    @Test
    fun `isZero returns false for non-zero`() {
        val q = Quantity.of(5)
        assertFalse(q.isZero())
    }

    @Test
    fun `plus adds quantities`() {
        val q1 = Quantity.of(5)
        val q2 = Quantity.of(3)
        val result = q1 + q2
        assertEquals(8, result.value)
    }

    @Test
    fun `minus subtracts quantities`() {
        val q1 = Quantity.of(5)
        val q2 = Quantity.of(3)
        val result = q1 - q2
        assertEquals(2, result.value)
    }

    @Test
    fun `minus can go negative since 4_1`() {
        val q1 = Quantity.of(3)
        val q2 = Quantity.of(5)
        val r = q1 - q2
        assertEquals(-2, r.value)
        assertTrue(r.isNegative())
    }
}
