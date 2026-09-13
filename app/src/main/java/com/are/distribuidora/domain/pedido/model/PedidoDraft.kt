package com.are.distribuidora.domain.pedido.model

import com.are.distribuidora.domain.pedido.DiscountType

/**
 * Borrador del pedido en curso, en términos de dominio.
 *
 * Es el estado del flujo de creación persistido localmente para poder retomarlo
 * si la app se cierra inesperadamente. Nunca viaja al backend.
 */
data class PedidoDraft(
    val vendedorId: String,
    val routeId: String,
    val cliente: ClienteSelection,
    /** Nombre del cliente para mostrarlo en el diálogo de recuperación. */
    val clienteNombre: String,
    val deliveryDate: String,
    val ivaEnabled: Boolean,
    val items: List<PedidoDraftItem>,
    val updatedAt: Long,
) {
    /** Número de productos distintos en el borrador. */
    val itemCount: Int get() = items.size

    /** Suma de unidades (no de líneas). */
    val unitCount: Int get() = items.sumOf { it.cantidad }
}

data class PedidoDraftItem(
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoAmount: Double,
    val descuentoPercent: Double,
    val descuentoType: DiscountType,
    val notes: String?,
    val category: String?,
    val imageUrl: String?,
    val barcode: String?,
) {
    /**
     * Los ítems personalizados no corresponden a ningún producto del catálogo, así
     * que la revalidación no puede ni debe buscarlos allí.
     * Mismo prefijo que usa `CreatePedidoFlowViewModel.addCustomItem`.
     */
    val isCustom: Boolean get() = productoId.startsWith(CUSTOM_ITEM_PREFIX)

    companion object {
        const val CUSTOM_ITEM_PREFIX = "custom_"
    }
}
