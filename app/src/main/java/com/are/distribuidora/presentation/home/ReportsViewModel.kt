package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.pedido.usecase.GetReportDataUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import com.are.distribuidora.core.result.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel para la pantalla de Reportes (Home).
 *
 * Delega toda la lógica de datos a [GetReportDataUseCase], siguiendo
 * Clean Architecture: el ViewModel solo transforma datos de dominio
 * en modelos de UI (formateo de moneda, fechas, etc.).
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getReportDataUseCase: GetReportDataUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
) : ViewModel() {

    // ── UI State ──────────────────────────────────────────────────────────────

    sealed class UiState {
        object Loading : UiState()
        data class Success(val data: ReportData) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }

    private val dayFormat = SimpleDateFormat("EEE", Locale("es", "GT"))
    private val shortDateFormat = SimpleDateFormat("d MMM", Locale("es", "GT"))
    private val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadReport()
    }

    fun refresh() {
        loadReport()
    }

    private fun loadReport() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Rango: últimos 14 días
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -14)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val sinceEpoch14 = cal.timeInMillis

                // Rango: últimos 7 días
                val cal7 = Calendar.getInstance()
                cal7.add(Calendar.DAY_OF_YEAR, -7)
                cal7.set(Calendar.HOUR_OF_DAY, 0)
                cal7.set(Calendar.MINUTE, 0)
                cal7.set(Calendar.SECOND, 0)
                cal7.set(Calendar.MILLISECOND, 0)
                val sinceEpoch7 = cal7.timeInMillis

                // Resolver nombres de ruta (dominio → presentación)
                val routeNames: Map<String, String> = when (val r = getRoutesUseCase()) {
                    is Result.Success -> r.value.associate { it.id to it.name }
                    else -> emptyMap()
                }

                // Use Case — sin ningún DAO en este ViewModel
                val report = getReportDataUseCase(
                    sinceEpoch14 = sinceEpoch14,
                    sinceEpoch7 = sinceEpoch7,
                )

                // Promedio diario
                val avgDaily = if (report.totalPedidos14 > 0) report.totalVentas14 / 14.0 else 0.0

                // Mapear dominio → UI models
                val routeSalesUi = report.routeSales.map { d ->
                    RouteSalesUi(
                        routeId = d.routeId,
                        routeName = routeNames[d.routeId] ?: d.routeId,
                        totalFormatted = currencyFormat.format(d.totalVentas),
                        totalAmount = d.totalVentas,
                        pedidoCount = d.pedidoCount,
                    )
                }

                val dailySalesUi = report.dailySales.map { d ->
                    val parsed = try { dbDateFormat.parse(d.dateLabel) } catch (_: Exception) { null }
                    DailySalesUi(
                        dateLabel = if (parsed != null) shortDateFormat.format(parsed) else d.dateLabel,
                        dayOfWeek = if (parsed != null) dayFormat.format(parsed) else "",
                        totalAmount = d.totalVentas,
                        pedidoCount = d.pedidoCount,
                    )
                }

                val topProductsUi = report.topProducts.map { d ->
                    TopProductUi(
                        productName = d.productName,
                        totalQty = d.totalQty,
                        totalRevenueFormatted = currencyFormat.format(d.totalRevenue),
                        totalRevenue = d.totalRevenue,
                        imageUrl = d.imageUrl,
                        imageLocalUri = d.imageLocalUri,
                    )
                }

                val bottomProductsUi = report.bottomProducts.map { d ->
                    TopProductUi(
                        productName = d.productName,
                        totalQty = d.totalQty,
                        totalRevenueFormatted = currencyFormat.format(d.totalRevenue),
                        totalRevenue = d.totalRevenue,
                        imageUrl = d.imageUrl,
                        imageLocalUri = d.imageLocalUri,
                    )
                }

                val topClientsUi = report.topClients.map { d ->
                    TopClientUi(
                        clientName = d.clienteNombre,
                        totalSpentFormatted = currencyFormat.format(d.totalSpent),
                        totalSpent = d.totalSpent,
                        orderCount = d.orderCount,
                    )
                }

                _uiState.value = UiState.Success(
                    ReportData(
                        totalVentas14Formatted = currencyFormat.format(report.totalVentas14),
                        totalPedidos14 = report.totalPedidos14,
                        totalVentas7Formatted = currencyFormat.format(report.totalVentas7),
                        totalPedidos7 = report.totalPedidos7,
                        avgDailyFormatted = currencyFormat.format(avgDaily),
                        routeSales = routeSalesUi,
                        dailySales = dailySalesUi,
                        topProducts = topProductsUi,
                        bottomProducts = bottomProductsUi,
                        topClients = topClientsUi,
                        hasData = report.totalPedidos14 > 0,
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error al cargar reportes: ${e.message}")
            }
        }
    }
}

// ── UI Models ─────────────────────────────────────────────────────────────────

data class ReportData(
    val totalVentas14Formatted: String,
    val totalPedidos14: Int,
    val totalVentas7Formatted: String,
    val totalPedidos7: Int,
    val avgDailyFormatted: String,
    val routeSales: List<RouteSalesUi>,
    val dailySales: List<DailySalesUi>,
    val topProducts: List<TopProductUi>,
    val bottomProducts: List<TopProductUi>,
    val topClients: List<TopClientUi>,
    val hasData: Boolean,
)

data class RouteSalesUi(
    val routeId: String,
    val routeName: String,
    val totalFormatted: String,
    val totalAmount: Double,
    val pedidoCount: Int,
)

data class DailySalesUi(
    val dateLabel: String,
    val dayOfWeek: String,
    val totalAmount: Double,
    val pedidoCount: Int,
)

data class TopProductUi(
    val productName: String,
    val totalQty: Int,
    val totalRevenueFormatted: String,
    val totalRevenue: Double,
    val imageUrl: String? = null,
    val imageLocalUri: String? = null,
)

data class TopClientUi(
    val clientName: String,
    val totalSpentFormatted: String,
    val totalSpent: Double,
    val orderCount: Int,
)
