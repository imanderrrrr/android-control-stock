package com.are.distribuidora.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductIdTest {

    @Test
    fun `of creates ProductId for valid format`() {
        val id = ProductId.of("PROD-001")
        assertEquals("PROD-001", id.value)
    }

    @Test
    fun `of throws for blank`() {
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("") }
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("   ") }
    }

    @Test
    fun `of throws for invalid characters`() {
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("prod-001") } // Lowercase not allowed by regex
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("PROD@@@") }
    }

    @Test
    fun `of throws for invalid length`() {
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("A") } // Too short
        assertThrows(IllegalArgumentException::class.java) { ProductId.of("A".repeat(21)) } // Too long
    }
}
