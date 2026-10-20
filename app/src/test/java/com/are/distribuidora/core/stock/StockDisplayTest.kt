package com.are.distribuidora.core.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contador del servidor puede ser negativo (lo vendido sin existencias). A la gente se le
 * muestra como en la 3.x: el stock se queda en 0 y lo que falta aparece como "comprometido".
 * Ejemplo de Anderson: hay 0 galletas, se venden 10 → "stock 0, comprometido 10".
 */
class StockDisplayTest {

    @Test
    fun `stock negativo se muestra como cero existencias y lo faltante comprometido`() {
        val d = StockDisplay.of(-10)

        assertEquals(0, d.onHand)
        assertEquals(10, d.committed)
        assertTrue(d.hasCommitted)
    }

    @Test
    fun `stock positivo no tiene comprometido`() {
        val d = StockDisplay.of(48)

        assertEquals(48, d.onHand)
        assertEquals(0, d.committed)
        assertFalse(d.hasCommitted)
    }

    @Test
    fun `stock cero no tiene comprometido`() {
        val d = StockDisplay.of(0)

        assertEquals(0, d.onHand)
        assertFalse(d.hasCommitted)
    }

    @Test
    fun `etiqueta de inventario con comprometido`() {
        assertEquals("Sin stock · 10 comprometidos", StockDisplay.of(-10).inventoryLabel())
        assertEquals("Sin stock · 1 comprometido", StockDisplay.of(-1).inventoryLabel())
    }

    @Test
    fun `etiqueta de inventario sin comprometido conserva los textos de siempre`() {
        assertEquals("Sin stock", StockDisplay.of(0).inventoryLabel())
        assertEquals("3 · bajo", StockDisplay.of(3).inventoryLabel())
        assertEquals("5 · bajo", StockDisplay.of(5).inventoryLabel())
        assertEquals("48 en stock", StockDisplay.of(48).inventoryLabel())
    }

    @Test
    fun `etiqueta del catalogo de pedido`() {
        assertEquals("STOCK 48", StockDisplay.of(48).catalogLabel())
        assertEquals("STOCK 0", StockDisplay.of(0).catalogLabel())
        assertEquals("STOCK 0 · COMP. 10", StockDisplay.of(-10).catalogLabel())
    }

    @Test
    fun `texto corto para vales y busquedas`() {
        assertEquals("48", StockDisplay.of(48).shortText())
        assertEquals("0", StockDisplay.of(0).shortText())
        assertEquals("0 · 10 comprometidos", StockDisplay.of(-10).shortText())
        assertEquals("0 · 1 comprometido", StockDisplay.of(-1).shortText())
    }

    @Test
    fun `texto del comprometido para avisos`() {
        assertEquals("10 comprometidos", StockDisplay.of(-10).committedText())
        assertEquals("1 comprometido", StockDisplay.of(-1).committedText())
        assertEquals("", StockDisplay.of(5).committedText())
    }
}
