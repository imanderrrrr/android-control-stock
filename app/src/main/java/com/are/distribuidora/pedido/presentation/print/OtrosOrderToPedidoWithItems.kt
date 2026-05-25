package com.are.distribuidora.pedido.presentation.print

import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoItem
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderItem

/**
 * Convierte un pedido descargado desde "Otros Pedidos" en un [PedidoWithItems]
 * sintético que pueda alimentar a [PrintTicketHelper] y [PdfTicketHelper].
 *
 * Razón: los helpers de impresión y compartido de PDF están construidos sobre
 * [PedidoWithItems] (modelo de "Mis Pedidos"). En el flujo de "Otros" tenemos
 * solo [Order] + [List<OrderItem>] (snapshot remoto, sin descuentos). Para
 * mantener consistencia visual del ticket sin duplicar el helper, mapeamos
 * los datos disponibles y rellenamos con valores neutros lo que no aplica.
 *
 * Campos que NO existen en Otros Pedidos y se neutralizan:
 *   - [PedidoItem.descuentoItem] = 0.0 (los items remotos vienen sin descuento expuesto).
 *   - [Pedido.descuentoGlobal] = 0.0.
 *   - [PedidoItem.id] = productoId (único dentro del pedido; el helper no usa este campo
 *     para impresión, solo para DiffUtil de listas, que aquí no aplica).
 *   - Teléfono del cliente: null (no se descarga en el header).
 */
internal fun buildPedidoWithItemsForPrint(
    order: Order,
    items: List<OrderItem>,
): PedidoWithItems {
    val pedidoItems = items.map { item ->
        PedidoItem(
            id             = item.productId,
            productoId     = item.productId,
            nombre         = item.productName,
            precioUnitario = item.unitPrice,
            cantidad       = item.quantity,
            descuentoItem  = 0.0,
            totalItem      = item.lineTotal,
            notes          = item.notes?.takeIf { it.isNotBlank() },
        )
    }

    val subtotal = pedidoItems.sumOf { it.totalItem }
    val total    = order.totalAmount ?: subtotal

    val pedido = Pedido(
        id              = order.orderId,
        vendedorId      = order.sellerName.orEmpty(),
        routeId         = order.routeId,
        deliveryDate    = order.deliveryDate,
        clienteId       = null,
        clienteSnapshot = ClienteSnapshot(
            nombre    = order.clientName,
            telefono  = null,
            direccion = order.clientAddress,
        ),
        items           = pedidoItems,
        subtotal        = subtotal,
        descuentoGlobal = 0.0,
        total           = total,
        version         = 0,
        actualizadoPor  = order.sellerName.orEmpty(),
        creadoEn        = order.createdAt,
        actualizadoEn   = order.updatedAt,
    )

    return PedidoWithItems(pedido = pedido, items = pedidoItems)
}
