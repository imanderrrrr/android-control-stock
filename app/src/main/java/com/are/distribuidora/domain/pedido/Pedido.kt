package com.are.distribuidora.domain.pedido

import com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase

/**
 * Contrato de datos del Pedido y funciones de dominio.
 * No contiene acceso a Firestore ni lógica de sincronización.
 */
data class Pedido(
    val id: String,                    // DocID de Firestore
    val vendedorId: String,
    val routeId: String,               // Ruta del cliente
    /** Fecha de entrega "YYYY-MM-DD". Requerida para Firestore query al descargar. */
    val deliveryDate: String = "",
    val clienteId: String?,
    val clienteSnapshot: com.are.distribuidora.domain.pedido.model.ClienteSnapshot,
    val items: List<PedidoItem>,
    val subtotal: Double,
    val descuentoGlobal: Double,
    val total: Double,
    // estado eliminado
    val version: Int,
    val actualizadoPor: String,
    val creadoEn: Long,                // epoch millis
    val actualizadoEn: Long            // epoch millis
) {

    /**
     * Regla 5 — Cálculo de totales:
     * - totalItem = redondeo_Q0.25( (precioUnitario * cantidad) - descuentoItem )
     * - subtotal  = redondeo_Q0.25( suma(totalItem) )
     * - total     = redondeo_Q0.25( subtotal - descuentoGlobal )
     *
     * Todos los montos se redondean al múltiplo de Q 0.25 más cercano para que el
     * monto mostrado al cliente y el monto persistido correspondan con las
     * denominaciones monetarias circulantes en Guatemala (0, 25, 50, 75 centavos).
     *
     * Devuelve un nuevo Pedido recalculando los totales de snapshot. No altera otros campos.
     */
    fun recalcularTotales(): Pedido {
        val nuevosItems = items.map { it.copy(totalItem = it.calcularTotalItem()) }
        val nuevoSubtotal = RoundToQuarterQuetzalUseCase(nuevosItems.sumOf { it.totalItem })
        val nuevoTotal = RoundToQuarterQuetzalUseCase(
            (nuevoSubtotal - descuentoGlobal).coerceAtLeast(0.0)
        )
        return copy(
            items = nuevosItems,
            subtotal = nuevoSubtotal,
            total = nuevoTotal
        )
    }

    /**
     * Regla 2 — Versionado:
     * Incrementa la versión en +1, setea actualizadoPor y actualizadoEn.
     * La llamada debe hacerse solo cuando la operación completa fue exitosa (Regla 4).
     */
    fun incrementarVersion(usuarioActualId: String, ahoraEpochMillis: Long): Pedido {
        return copy(
            version = this.version + 1,
            actualizadoPor = usuarioActualId,
            actualizadoEn = ahoraEpochMillis
        )
    }

    /**
     * Regla 3 — LWW (Última escritura gana):
     * Compara por versión entre pedidos con el mismo id.
     */
    fun esMasRecienteQue(otro: Pedido): Boolean {
        require(this.id == otro.id) { "Comparación LWW requiere el mismo id de pedido." }
        return this.version > otro.version
    }
}

