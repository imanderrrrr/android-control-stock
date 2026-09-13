package com.are.distribuidora.pedido.presentation.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formato único de fecha + hora de creación de un pedido: "d MMM yyyy · HH:mm".
 *
 * La hora es la del instante en que el vendedor CONFIRMÓ el carrito (`creadoEn`,
 * fijado en `PedidoRepositoryImpl.createPedido`), que es lo que gerencia pidió ver
 * en Mis pedidos, Otros pedidos y el detalle.
 */
class PedidoDateTimeFormatter(
    locale: Locale = Locale("es", "GT"),
    timeZone: TimeZone = TimeZone.getDefault(),
) {
    private val dateTime = SimpleDateFormat(PATTERN, locale).apply { this.timeZone = timeZone }
    private val dateOnly = SimpleDateFormat(DATE_PATTERN, locale).apply { this.timeZone = timeZone }

    /** "8 sept 2026 · 14:05" */
    fun formatDateTime(epochMillis: Long): String = dateTime.format(Date(epochMillis))

    /** "8 sept 2026" (para encabezados y filtros donde la hora no aplica). */
    fun formatDate(epochMillis: Long): String = dateOnly.format(Date(epochMillis))

    companion object {
        const val PATTERN = "d MMM yyyy · HH:mm"
        const val DATE_PATTERN = "d MMM yyyy"
    }
}
