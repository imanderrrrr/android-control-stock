package com.are.distribuidora.domain.pedido

import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de lógica de dominio pura para Pedido y PedidoItem.
 * No requieren Android ni corrutinas.
 */
class PedidoTest {

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun buildItem(
        id: String = "item-1",
        productoId: String = "prod-1",
        nombre: String = "Producto A",
        precioUnitario: Double = 10.0,
        cantidad: Int = 2,
        descuentoItem: Double = 0.0,
        totalItem: Double = (precioUnitario * cantidad) - descuentoItem,
    ) = PedidoItem(
        id = id,
        productoId = productoId,
        nombre = nombre,
        precioUnitario = precioUnitario,
        cantidad = cantidad,
        descuentoItem = descuentoItem,
        totalItem = totalItem,
    )

    private fun buildPedido(
        id: String = "pedido-1",
        routeId: String = "ruta-1",
        items: List<PedidoItem> = listOf(buildItem()),
        subtotal: Double = items.sumOf { it.totalItem },
        descuentoGlobal: Double = 0.0,
        total: Double = subtotal - descuentoGlobal,
        version: Int = 1,
        vendedorId: String = "vendedor-1",
    ) = Pedido(
        id = id,
        vendedorId = vendedorId,
        routeId = routeId,
        clienteId = "cliente-1",
        clienteSnapshot = ClienteSnapshot("Juan Pérez", "1234567", "Calle 1"),
        items = items,
        subtotal = subtotal,
        descuentoGlobal = descuentoGlobal,
        total = total,
        version = version,
        actualizadoPor = vendedorId,
        creadoEn = 1_700_000_000_000L,
        actualizadoEn = 1_700_000_000_000L,
    )

    // ─── PedidoItem.calcularTotalItem ────────────────────────────────────────

    @Test
    fun `calcularTotalItem sin descuento es precio por cantidad`() {
        val item = buildItem(precioUnitario = 10.0, cantidad = 3, descuentoItem = 0.0)
        assertEquals(30.0, item.calcularTotalItem(), 0.001)
    }

    @Test
    fun `calcularTotalItem con descuento descuenta correctamente`() {
        val item = buildItem(precioUnitario = 10.0, cantidad = 3, descuentoItem = 5.0)
        assertEquals(25.0, item.calcularTotalItem(), 0.001)
    }

    @Test
    fun `calcularTotalItem con cantidad 1`() {
        val item = buildItem(precioUnitario = 25.50, cantidad = 1, descuentoItem = 0.0)
        assertEquals(25.50, item.calcularTotalItem(), 0.001)
    }

    // ─── Pedido.recalcularTotales ─────────────────────────────────────────────

    @Test
    fun `recalcularTotales sin descuento global calcula subtotal correcto`() {
        val items = listOf(
            buildItem(id = "i1", precioUnitario = 10.0, cantidad = 2, descuentoItem = 0.0, totalItem = 999.0), // totalItem incorrecto
            buildItem(id = "i2", precioUnitario = 5.0, cantidad = 4, descuentoItem = 0.0, totalItem = 999.0),
        )
        val pedido = buildPedido(items = items, subtotal = 0.0, total = 0.0, descuentoGlobal = 0.0)

        val resultado = pedido.recalcularTotales()

        // item1: 10*2=20, item2: 5*4=20 → subtotal=40, total=40
        assertEquals(40.0, resultado.subtotal, 0.001)
        assertEquals(40.0, resultado.total, 0.001)
        assertEquals(20.0, resultado.items[0].totalItem, 0.001)
        assertEquals(20.0, resultado.items[1].totalItem, 0.001)
    }

    @Test
    fun `recalcularTotales con descuento global reduce el total`() {
        val items = listOf(
            buildItem(id = "i1", precioUnitario = 50.0, cantidad = 2, descuentoItem = 0.0, totalItem = 999.0),
        )
        val pedido = buildPedido(items = items, subtotal = 0.0, total = 0.0, descuentoGlobal = 15.0)

        val resultado = pedido.recalcularTotales()

        assertEquals(100.0, resultado.subtotal, 0.001)
        assertEquals(85.0, resultado.total, 0.001)
    }

    @Test
    fun `recalcularTotales con descuento en items y global`() {
        val items = listOf(
            buildItem(id = "i1", precioUnitario = 10.0, cantidad = 3, descuentoItem = 5.0, totalItem = 999.0),
        )
        val pedido = buildPedido(items = items, subtotal = 0.0, total = 0.0, descuentoGlobal = 10.0)

        val resultado = pedido.recalcularTotales()

        // item: (10*3)-5 = 25 → subtotal=25, total=25-10=15
        assertEquals(25.0, resultado.subtotal, 0.001)
        assertEquals(15.0, resultado.total, 0.001)
    }

    // ─── Pedido.incrementarVersion ────────────────────────────────────────────

    @Test
    fun `incrementarVersion aumenta version en 1`() {
        val pedido = buildPedido(version = 3)
        val actualizado = pedido.incrementarVersion("vendedor-2", 1_700_000_001_000L)
        assertEquals(4, actualizado.version)
    }

    @Test
    fun `incrementarVersion actualiza actualizadoPor`() {
        val pedido = buildPedido(vendedorId = "vendedor-1")
        val actualizado = pedido.incrementarVersion("vendedor-99", 1_700_000_001_000L)
        assertEquals("vendedor-99", actualizado.actualizadoPor)
    }

    @Test
    fun `incrementarVersion actualiza actualizadoEn`() {
        val pedido = buildPedido()
        val nuevaFecha = 1_800_000_000_000L
        val actualizado = pedido.incrementarVersion("vendedor-1", nuevaFecha)
        assertEquals(nuevaFecha, actualizado.actualizadoEn)
    }

    @Test
    fun `incrementarVersion no altera otros campos`() {
        val pedido = buildPedido(id = "p-99", routeId = "r-5", version = 1)
        val actualizado = pedido.incrementarVersion("v", 0L)
        assertEquals("p-99", actualizado.id)
        assertEquals("r-5", actualizado.routeId)
    }

    // ─── Pedido.esMasRecienteQue ──────────────────────────────────────────────

    @Test
    fun `esMasRecienteQue retorna true si version propia es mayor`() {
        val nuevo = buildPedido(id = "p-1", version = 5)
        val viejo = buildPedido(id = "p-1", version = 3)
        assertTrue(nuevo.esMasRecienteQue(viejo))
    }

    @Test
    fun `esMasRecienteQue retorna false si version propia es menor`() {
        val viejo = buildPedido(id = "p-1", version = 2)
        val nuevo = buildPedido(id = "p-1", version = 7)
        assertFalse(viejo.esMasRecienteQue(nuevo))
    }

    @Test
    fun `esMasRecienteQue retorna false si versiones son iguales`() {
        val a = buildPedido(id = "p-1", version = 4)
        val b = buildPedido(id = "p-1", version = 4)
        assertFalse(a.esMasRecienteQue(b))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `esMasRecienteQue lanza excepcion si ids son distintos`() {
        val a = buildPedido(id = "p-1", version = 5)
        val b = buildPedido(id = "p-2", version = 1)
        a.esMasRecienteQue(b)
    }
}

