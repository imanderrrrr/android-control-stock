package com.are.distribuidora.domain.pedido.model

/**
 * Parámetros para solicitar el reporte de ventas.
 * @param sinceEpoch14 Timestamp en millis del inicio del período de 14 días.
 * @param sinceEpoch7  Timestamp en millis del inicio del período de 7 días.
 * @param topLimit     Cantidad máxima de items en las listas top/bottom.
 */
data class ReportParams(
    val sinceEpoch14: Long,
    val sinceEpoch7: Long,
    val topLimit: Int = 5,
)

/**
 * Datos crudos de reporte devueltos por el dominio.
 * Sin formateo de moneda ni lógica de presentación — eso es responsabilidad de la UI.
 */
data class ReportResult(
    val totalVentas14: Double,
    val totalPedidos14: Int,
    val totalVentas7: Double,
    val totalPedidos7: Int,
    val routeSales: List<RouteSalesData>,
    val dailySales: List<DailySalesData>,
    val topProducts: List<TopProductData>,
    val bottomProducts: List<TopProductData>,
    val topClients: List<TopClientData>,
)

data class RouteSalesData(
    val routeId: String,
    val totalVentas: Double,
    val pedidoCount: Int,
)

data class DailySalesData(
    val dateLabel: String,   // formato "YYYY-MM-DD"
    val totalVentas: Double,
    val pedidoCount: Int,
)

data class TopProductData(
    val productoId: String,
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Double,
    val imageUrl: String?,
    val imageLocalUri: String?,
)

data class TopClientData(
    val clienteNombre: String,
    val totalSpent: Double,
    val orderCount: Int,
)

