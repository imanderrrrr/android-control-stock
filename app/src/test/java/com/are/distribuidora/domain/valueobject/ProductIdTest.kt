package com.are.distribuidora.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductIdTest {

    @Test
    fun `of creates ProductId for non-empty ids`() {
        val id = ProductId.of("PROD-001")
        assertEquals("PROD-001", id.value)
    }

    @Test
    fun `of trims whitespace`() {
        val id = ProductId.of("  PROD-001  ")
        assertEquals("PROD-001", id.value)
    }

    @Test
    fun `of throws for blank`() {
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("") }
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("   ") }
    }
}
