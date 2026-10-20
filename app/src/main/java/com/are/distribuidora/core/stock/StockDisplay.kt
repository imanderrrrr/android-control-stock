package com.are.distribuidora.core.stock

/**
 * Cómo se le muestra el contador de stock a la gente.
 *
 * Desde 4.1 el contador del servidor puede ser negativo: es lo vendido sin existencias, y se
 * cubre solo cuando entra mercadería con un vale de entrada (−10 + 30 = 20). En pantalla se
 * muestra como en la 3.x: el stock se queda en 0 y lo faltante aparece como "comprometido".
 * Es SOLO presentación: el libro de movimientos y el servidor siguen trabajando con el número
 * con signo, y nada fuera de la UI debe usar esta clase para calcular.
 */
data class StockDisplay(val onHand: Int, val committed: Int) {

    val hasCommitted: Boolean get() = committed > 0

    /** Insignia de Inventario: "48 en stock", "3 · bajo", "Sin stock", "Sin stock · 10 comprometidos". */
    fun inventoryLabel(): String = when {
        hasCommitted -> "Sin stock · ${committedText()}"
        onHand == 0 -> "Sin stock"
        onHand <= LOW_STOCK_THRESHOLD -> "$onHand · bajo"
        else -> "$onHand en stock"
    }

    /** Insignia del catálogo de pedido (espacio corto): "STOCK 48", "STOCK 0 · COMP. 10". */
    fun catalogLabel(): String =
        if (hasCommitted) "STOCK 0 · COMP. $committed" else "STOCK $onHand"

    /** Texto corto para vales y búsquedas: "48", "0 · 10 comprometidos". */
    fun shortText(): String =
        if (hasCommitted) "0 · ${committedText()}" else "$onHand"

    /** "10 comprometidos" / "1 comprometido"; vacío si no hay nada comprometido. */
    fun committedText(): String = when {
        !hasCommitted -> ""
        committed == 1 -> "1 comprometido"
        else -> "$committed comprometidos"
    }

    companion object {
        /** Hasta este número el stock se marca como bajo en Inventario. */
        const val LOW_STOCK_THRESHOLD = 5

        fun of(stock: Int): StockDisplay =
            StockDisplay(onHand = stock.coerceAtLeast(0), committed = (-stock).coerceAtLeast(0))
    }
}
