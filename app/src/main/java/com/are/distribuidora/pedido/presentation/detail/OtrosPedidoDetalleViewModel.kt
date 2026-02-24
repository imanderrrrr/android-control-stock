package com.are.distribuidora.pedido.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.orders.domain.usecase.GetOtrosPedidoDetalleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel para el detalle de un pedido de "Otros Pedidos".
 *
 * Consulta Room directamente via [GetOtrosPedidoDetalleUseCase].
 * NO usa allPedidos ni ningún dato de "Mis Pedidos".
 */
@HiltViewModel
class OtrosPedidoDetalleViewModel @Inject constructor(
    private val getDetalle: GetOtrosPedidoDetalleUseCase,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        object Empty : UiState()
        data class Success(
            val clientName: String,
            val sellerName: String?,
            val totalFormatted: String,
            val items: List<OtrosDetalleItemUiModel>,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }

    fun load(orderId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = getDetalle(orderId)
                val order = result.order
                val items = result.items

                when {
                    order == null -> _uiState.value = UiState.Empty
                    items.isEmpty() -> _uiState.value = UiState.Empty
                    else -> {
                        val uiItems = items.map { item ->
                            OtrosDetalleItemUiModel(
                                productId          = item.productId,
                                productName        = item.productName,
                                unitPriceFormatted = currencyFormat.format(item.unitPrice),
                                quantity           = item.quantity,
                                lineTotalFormatted = currencyFormat.format(item.lineTotal),
                            )
                        }
                        // Total: usa totalAmount del header si existe; si no, suma de líneas.
                        val total = order.totalAmount
                            ?: items.sumOf { it.lineTotal }
                        _uiState.value = UiState.Success(
                            clientName    = order.clientName,
                            sellerName    = order.sellerName,
                            totalFormatted = currencyFormat.format(total),
                            items         = uiItems,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error al cargar el pedido")
            }
        }
    }
}

/** Modelo de UI para cada ítem del detalle de Otros Pedidos. */
data class OtrosDetalleItemUiModel(
    val productId: String,
    val productName: String,
    val unitPriceFormatted: String,   // "Q 25.00"
    val quantity: Int,
    val lineTotalFormatted: String,   // "Q 50.00"
)

