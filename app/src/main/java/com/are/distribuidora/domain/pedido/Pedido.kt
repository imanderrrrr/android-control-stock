package com.are.distribuidora.domain.pedido

/**
 * Contrato de datos del Pedido y funciones de dominio.
 * No contiene acceso a Firestore ni lógica de sincronización.
 */
data class Pedido(
    val id: String,                    // DocID de Firestore
    val vendedorId: String,
    val routeId: String,               // Ruta del cliente
    val clienteId: String?,
    val items: List<PedidoItem>,
    val subtotal: Double,
    val descuentoGlobal: Double,
    val total: Double,
    val estado: PedidoEstado,          // ABIERTO | CERRADO
    val version: Int,
    val actualizadoPor: String,
    val creadoEn: Long,                // epoch millis
    val actualizadoEn: Long            // epoch millis
) {

    /**
     * Regla 1 — Editabilidad:
     * Un pedido ABIERTO puede modificarse. Uno CERRADO no.
     */
    fun puedeModificarse(): Boolean = estado == PedidoEstado.ABIERTO

    /**
     * Regla 5 — Cálculo de totales:
     * - totalItem = (precioUnitario * cantidad) - descuentoItem
     * - subtotal = suma(totalItem)
     * - total = subtotal - descuentoGlobal
     *
     * Devuelve un nuevo Pedido recalculando los totales de snapshot.
     * No altera otros campos.
     */
    fun recalcularTotales(): Pedido {
        val nuevosItems = items.map { it.copy(totalItem = it.calcularTotalItem()) }
        val nuevoSubtotal = nuevosItems.sumOf { it.totalItem }
        val nuevoTotal = nuevoSubtotal - descuentoGlobal
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

