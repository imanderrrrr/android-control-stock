package com.are.distribuidora.domain.model

import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ProductTest {

    private val defaultPrice = Money.of(BigDecimal("10.00"))

    @Test
    fun `sell reduces stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(10),
            price = defaultPrice
        )

        val updated = product.sell(Quantity.of(3))

        assertEquals(Quantity.of(7), updated.stock)
    }

    @Test
    fun `reserve reduces stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(10),
            price = defaultPrice
        )

        val updated = product.reserve(Quantity.of(3))

        assertEquals(Quantity.of(7), updated.stock)
    }

    @Test
    fun `release increases stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(5),
            price = defaultPrice
        )

        val updated = product.release(Quantity.of(2))

        assertEquals(Quantity.of(7), updated.stock)
    }

    @Test
    fun `canSell returns true if sufficient stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(5),
            price = defaultPrice
        )

        assertTrue(product.canSell(Quantity.of(5)))
        assertTrue(product.canSell(Quantity.of(1)))
    }

    @Test
    fun `canSell returns false if insufficient stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(5),
            price = defaultPrice
        )

        assertFalse(product.canSell(Quantity.of(6)))
    }

    @Test
    fun `sell throws if insufficient stock`() {
        val product = Product(
            id = ProductId.of("ABC123"),
            name = "Test Product",
            stock = Quantity.of(2),
            price = defaultPrice
        )

        assertThrows(IllegalArgumentException::class.java) {
            product.sell(Quantity.of(3))
        }
    }
}
