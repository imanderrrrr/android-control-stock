package com.are.distribuidora.domain.pedido

/**
 * Snapshot de un ítem del pedido.
 * Los totales se calculan como snapshot y no se recalculan dinámicamente desde producto.
 */
data class PedidoItem(
    val productoId: String,     // DocID del producto en Firestore
    val nombre: String,         // snapshot
    val precioUnitario: Double, // snapshot
    val cantidad: Int,
    val descuentoItem: Double,
    val totalItem: Double       // snapshot calculado: (precioUnitario * cantidad) - descuentoItem
) {
    /**
     * Regla 5 — Cálculo de totales para item:
     * totalItem = (precioUnitario * cantidad) - descuentoItem
     */
    fun calcularTotalItem(): Double {
        return (precioUnitario * cantidad) - descuentoItem
    }
}

