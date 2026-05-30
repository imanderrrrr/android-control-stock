package com.are.distribuidora.domain.pedido

import com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase

/**
 * Snapshot de un ítem del pedido.
 * Los totales se calculan como snapshot y no se recalculan dinámicamente desde producto.
 */
data class PedidoItem(
    val id: String,             // UUID local
    val productoId: String,     // DocID del producto en Firestore
    val nombre: String,         // snapshot
    val precioUnitario: Double, // snapshot
    val cantidad: Int,
    val descuentoItem: Double,
    val totalItem: Double,      // snapshot calculado y redondeado al múltiplo de Q 0.25 más cercano
    val notes: String? = null,  // detalle / instrucción especial del cliente para este ítem
) {
    /**
     * Regla 5 — Cálculo de totales para item:
     * totalItem = redondeo_Q0.25( (precioUnitario * cantidad) - descuentoItem )
     *
     * El redondeo al múltiplo de Q 0.25 es obligatorio: en Guatemala solo circulan
     * monedas de 0, 25, 50 y 75 centavos, por lo que el monto mostrado al cliente y
     * el monto persistido deben coincidir con una de esas denominaciones.
     */
    fun calcularTotalItem(): Double {
        val bruto = (precioUnitario * cantidad) - descuentoItem
        return RoundToQuarterQuetzalUseCase(bruto.coerceAtLeast(0.0))
    }
}

