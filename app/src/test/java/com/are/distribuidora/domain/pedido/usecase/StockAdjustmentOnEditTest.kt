package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.model.EditPedidoItemInput
import com.are.distribuidora.domain.pedido.model.PreviousItemSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitarios que verifican el ajuste CORRECTO de stock al editar un pedido.
 *
 * Cubre los escenarios del bug original:
 *  1. Reducir cantidad de un ítem → restaurar la diferencia al stock.
 *  2. Aumentar cantidad de un ítem → descontar la diferencia del stock.
 *  3. Eliminar un ítem completo   → restaurar TODA su cantidad al stock.
 *  4. Agregar un ítem nuevo       → descontar TODA su cantidad del stock.
 *  5. Sin cambios de cantidad     → delta = 0, stock sin tocar.
 *  6. Múltiples ítems mixtos      → cada uno ajusta correctamente.
 *
 * La lógica de ajuste de stock vive en PedidoRepositoryImpl.editPedido().
 * Aquí se replica en un FakeRepository en memoria para que el test sea
 * puramente de JVM, sin Android ni Room.
 *
 * La lógica de la BD está replicada en helpers:
 *  - [applyDeduct]   → equivale a ProductDao.deductStockAndCommit
 *  - [applyRestore]  → equivale a ProductDao.restoreStock
 */
class StockAdjustmentOnEditTest {

    // ─── Modelo de stock en memoria ──────────────────────────────────────────

    data class StockState(val stock: Int, val comprometido: Int)

    /** Replica la lógica SQL de ProductDao.deductStockAndCommit */
    private fun applyDeduct(state: StockState, cantidad: Int): StockState {
        val descontado = minOf(state.stock, cantidad)
        val excedente  = maxOf(0, cantidad - state.stock)
        return StockState(
            stock        = state.stock - descontado,
            comprometido = state.comprometido + excedente,
        )
    }

    /** Replica la lógica SQL de ProductDao.restoreStock */
    private fun applyRestore(state: StockState, cantidad: Int): StockState =
        StockState(
            stock        = state.stock + cantidad,
            comprometido = maxOf(0, state.comprometido - cantidad),
        )

    // ─── Motor de simulación de editPedido ───────────────────────────────────

    /**
     * Simula el bloque de ajuste de stock que vive dentro de
     * PedidoRepositoryImpl.editPedido() y devuelve el estado final del stock
     * para cada productoId.
     *
     * @param initialStocks   estado inicial del stock por productoId
     * @param previousItems   snapshot de ítems ANTES de editar
     * @param itemsToUpsert   ítems tal como quedan DESPUÉS de editar
     * @param itemIdsToDelete IDs de ítems eliminados
     */
    private fun simulateEditStockAdjustment(
        initialStocks: Map<String, StockState>,
        previousItems: List<PreviousItemSnapshot>,
        itemsToUpsert: List<EditPedidoItemInput>,
        itemIdsToDelete: List<String>,
    ): Map<String, StockState> {

        val stocks = initialStocks.toMutableMap()

        val prevByItemId = previousItems.associateBy { it.itemId }

        // 1) Ítems eliminados → restaurar su cantidad completa
        val deleteRestores: Map<String, Int> = itemIdsToDelete
            .mapNotNull { itemId -> prevByItemId[itemId] }
            .groupBy { it.productoId }
            .mapValues { (_, snaps) -> snaps.sumOf { it.cantidad } }

        deleteRestores.forEach { (productoId, qty) ->
            if (qty > 0) {
                val current = stocks[productoId] ?: StockState(0, 0)
                stocks[productoId] = applyRestore(current, qty)
            }
        }

        // 2) Ítems upsert → calcular delta
        data class StockDelta(val productoId: String, val delta: Int)
        val upsertDeltas = itemsToUpsert.map { input ->
            val prevQty = if (input.itemId != null) prevByItemId[input.itemId]?.cantidad ?: 0 else 0
            StockDelta(input.productoId, input.cantidad - prevQty)
        }
        val upsertDeltaByProducto = upsertDeltas
            .groupBy { it.productoId }
            .mapValues { (_, ds) -> ds.sumOf { it.delta } }

        upsertDeltaByProducto.forEach { (productoId, delta) ->
            val current = stocks[productoId] ?: StockState(0, 0)
            stocks[productoId] = when {
                delta > 0 -> applyDeduct(current, delta)
                delta < 0 -> applyRestore(current, -delta)
                else      -> current
            }
        }

        return stocks
    }

    // ─── Helpers de construcción ─────────────────────────────────────────────

    private fun prevSnap(itemId: String, productoId: String, cantidad: Int) =
        PreviousItemSnapshot(itemId = itemId, productoId = productoId, cantidad = cantidad)

    private fun itemInput(
        itemId: String?,
        productoId: String,
        cantidad: Int,
    ) = EditPedidoItemInput(
        itemId         = itemId,
        productoId     = productoId,
        nombre         = "Producto $productoId",
        precioUnitario = 10.0,
        cantidad       = cantidad,
        descuentoItem  = 0.0,
    )

    // ─── Tests ───────────────────────────────────────────────────────────────

    /**
     * Escenario 1 del bug original:
     * Pedido creado con 10 unidades del ítem-1 (prod-A) → stock descontado en 10.
     * Al editar se reduce a 7 unidades (−3).
     * Resultado esperado: las 3 unidades deben REGRESAR al stock.
     */
    @Test
    fun `reducir cantidad de item restaura la diferencia al stock`() {
        // Stock después de crear el pedido con 10 unidades
        val initialStocks = mapOf("prod-A" to StockState(stock = 0, comprometido = 0))
        // (stock=0 porque las 10 unidades ya fueron descontadas al crear)

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-1", "prod-A", 10)),
            itemsToUpsert   = listOf(itemInput("item-1", "prod-A", 7)),
            itemIdsToDelete = emptyList(),
        )

        val finalStock = result["prod-A"]!!
        // delta = 7 - 10 = -3  →  restore(3)  →  stock = 0 + 3 = 3
        assertEquals(
            "Debe restaurar 3 unidades al stock (delta = -3)",
            3,
            finalStock.stock,
        )
        assertEquals("Comprometido no debe cambiar (era 0)", 0, finalStock.comprometido)
    }

    /**
     * Escenario 2 del bug original:
     * Pedido creado con 10 unidades del ítem-1 (prod-A) → stock ya descontado.
     * Al editar se sube a 14 unidades (+4).
     * Resultado esperado: 4 unidades adicionales deben DESCONTARSE del stock.
     */
    @Test
    fun `aumentar cantidad de item descuenta la diferencia del stock`() {
        // Stock disponible después de crear el pedido (solo quedan 5)
        val initialStocks = mapOf("prod-A" to StockState(stock = 5, comprometido = 0))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-1", "prod-A", 10)),
            itemsToUpsert   = listOf(itemInput("item-1", "prod-A", 14)),
            itemIdsToDelete = emptyList(),
        )

        val finalStock = result["prod-A"]!!
        // delta = 14 - 10 = +4  →  deduct(4)  →  stock = 5 - 4 = 1
        assertEquals(
            "Debe descontar 4 unidades del stock (delta = +4)",
            1,
            finalStock.stock,
        )
        assertEquals("Comprometido sigue en 0", 0, finalStock.comprometido)
    }

    /**
     * Escenario 3: Aumentar cantidad cuando no hay suficiente stock.
     * El excedente debe ir a comprometido.
     */
    @Test
    fun `aumentar cantidad sin stock suficiente mueve el excedente a comprometido`() {
        // Solo quedan 2 unidades en stock
        val initialStocks = mapOf("prod-B" to StockState(stock = 2, comprometido = 0))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-2", "prod-B", 5)),
            itemsToUpsert   = listOf(itemInput("item-2", "prod-B", 10)),
            itemIdsToDelete = emptyList(),
        )

        val finalStock = result["prod-B"]!!
        // delta = 10 - 5 = +5  →  deduct(5) con stock=2
        //   stock' = 2 - min(2,5) = 0
        //   comprometido' = 0 + max(0, 5-2) = 3
        assertEquals("Stock agotado al 0", 0, finalStock.stock)
        assertEquals("Excedente de 3 pasa a comprometido", 3, finalStock.comprometido)
    }

    /**
     * Escenario 4: Eliminar ítem completo.
     * Toda la cantidad del ítem debe regresar al stock.
     */
    @Test
    fun `eliminar item completo devuelve todas las unidades al stock`() {
        // El stock fue completamente consumido por las 10 unidades del pedido
        val initialStocks = mapOf("prod-C" to StockState(stock = 0, comprometido = 0))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-3", "prod-C", 10)),
            itemsToUpsert   = emptyList(),          // ítem eliminado no aparece en upsert
            itemIdsToDelete = listOf("item-3"),
        )

        val finalStock = result["prod-C"]!!
        // restore(10)  →  stock = 0 + 10 = 10
        assertEquals("10 unidades regresan al stock al eliminar el ítem", 10, finalStock.stock)
        assertEquals("Comprometido queda en 0", 0, finalStock.comprometido)
    }

    /**
     * Escenario 5: Agregar un ítem nuevo al pedido.
     * Las unidades del ítem nuevo deben descontarse del stock.
     */
    @Test
    fun `agregar item nuevo descuenta sus unidades del stock`() {
        val initialStocks = mapOf("prod-D" to StockState(stock = 15, comprometido = 0))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = emptyList(),          // no había ítem anterior para prod-D
            itemsToUpsert   = listOf(itemInput(null, "prod-D", 6)),   // nuevo (itemId=null)
            itemIdsToDelete = emptyList(),
        )

        val finalStock = result["prod-D"]!!
        // prevQty = 0 (nuevo) → delta = 6 - 0 = +6  →  deduct(6)  →  stock = 15 - 6 = 9
        assertEquals("Debe descontar 6 unidades del stock al agregar ítem nuevo", 9, finalStock.stock)
        assertEquals("Comprometido sigue en 0", 0, finalStock.comprometido)
    }

    /**
     * Escenario 6: Sin cambios de cantidad.
     * El stock NO debe modificarse.
     */
    @Test
    fun `sin cambio de cantidad el stock no se modifica`() {
        val initialStocks = mapOf("prod-E" to StockState(stock = 8, comprometido = 2))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-5", "prod-E", 5)),
            itemsToUpsert   = listOf(itemInput("item-5", "prod-E", 5)),  // misma cantidad
            itemIdsToDelete = emptyList(),
        )

        val finalStock = result["prod-E"]!!
        // delta = 5 - 5 = 0  →  sin cambio
        assertEquals("Stock no debe cambiar si la cantidad es igual", 8, finalStock.stock)
        assertEquals("Comprometido no debe cambiar", 2, finalStock.comprometido)
    }

    /**
     * Escenario 7: Múltiples ítems de productos distintos con cambios mixtos.
     * Cada producto ajusta su stock de forma independiente y correcta.
     */
    @Test
    fun `multiples items con cambios mixtos ajustan stock correctamente por producto`() {
        val initialStocks = mapOf(
            "prod-X" to StockState(stock = 0, comprometido = 0),  // tenía 8, ya descontado
            "prod-Y" to StockState(stock = 10, comprometido = 0), // tenía 3, aún hay stock
            "prod-Z" to StockState(stock = 5, comprometido = 0),  // ítem nuevo
        )

        val result = simulateEditStockAdjustment(
            initialStocks = initialStocks,
            previousItems = listOf(
                prevSnap("item-X", "prod-X", 8),  // reducir de 8 a 5
                prevSnap("item-Y", "prod-Y", 3),  // aumentar de 3 a 6
                // prod-Z no tenía ítem previo
            ),
            itemsToUpsert = listOf(
                itemInput("item-X", "prod-X", 5),   // reduce 3 → restore(3)
                itemInput("item-Y", "prod-Y", 6),   // aumenta 3 → deduct(3)
                itemInput(null,     "prod-Z", 4),   // nuevo → deduct(4)
            ),
            itemIdsToDelete = emptyList(),
        )

        // prod-X: restore(3) → stock = 0 + 3 = 3
        assertEquals("prod-X: restaura 3 unidades", 3, result["prod-X"]!!.stock)

        // prod-Y: deduct(3) → stock = 10 - 3 = 7
        assertEquals("prod-Y: descuenta 3 unidades", 7, result["prod-Y"]!!.stock)

        // prod-Z: nuevo ítem, deduct(4) → stock = 5 - 4 = 1
        assertEquals("prod-Z: descuenta 4 unidades (ítem nuevo)", 1, result["prod-Z"]!!.stock)
    }

    /**
     * Escenario 8: Ítem eliminado cuando parte de la cantidad estaba en comprometido.
     * La restauración reduce comprometido primero, sin bajar de cero.
     */
    @Test
    fun `eliminar item con comprometido reduce comprometido correctamente`() {
        // 5 unidades pedidas, stock=0 y comprometido=5 (no había stock al crear)
        val initialStocks = mapOf("prod-F" to StockState(stock = 0, comprometido = 5))

        val result = simulateEditStockAdjustment(
            initialStocks   = initialStocks,
            previousItems   = listOf(prevSnap("item-F", "prod-F", 5)),
            itemsToUpsert   = emptyList(),
            itemIdsToDelete = listOf("item-F"),
        )

        val finalStock = result["prod-F"]!!
        // restore(5): stock = 0 + 5 = 5, comprometido = max(0, 5-5) = 0
        assertEquals("Stock recuperado = 5", 5, finalStock.stock)
        assertEquals("Comprometido vuelve a 0", 0, finalStock.comprometido)
    }
}


