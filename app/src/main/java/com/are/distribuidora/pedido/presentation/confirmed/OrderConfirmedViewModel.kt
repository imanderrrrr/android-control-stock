package com.are.distribuidora.pedido.presentation.confirmed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
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
 * Carga el pedido recién creado (por id) para la pantalla de confirmación.
 * Solo lee de Room; NO modifica nada (el pedido ya fue creado por OrderCartViewModel).
 */
@HiltViewModel
class OrderConfirmedViewModel @Inject constructor(
    private val pedidoRepository: PedidoRepository,
) : ViewModel() {

    data class UiModel(
        val orderRef: String,
        val clientName: String,
        val clientAddress: String?,
        val itemsCount: Int,
        val unitsCount: Int,
        val totalFormatted: String,
        val totalAmount: Double,
    )

    private val _uiModel = MutableStateFlow<UiModel?>(null)
    val uiModel: StateFlow<UiModel?> = _uiModel.asStateFlow()

    private var started = false

    fun load(pedidoId: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            pedidoRepository.observePedidoWithItems(pedidoId).collect { pwi ->
                if (pwi != null) _uiModel.value = pwi.toUiModel()
            }
        }
    }

    private fun PedidoWithItems.toUiModel(): UiModel {
        val nf = NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
        return UiModel(
            orderRef       = "#" + pedido.id.takeLast(6).uppercase(Locale("es", "GT")),
            clientName     = pedido.clienteSnapshot.nombre,
            clientAddress  = pedido.clienteSnapshot.direccion,
            itemsCount     = items.size,
            unitsCount     = items.sumOf { it.cantidad },
            totalFormatted = nf.format(pedido.total),
            totalAmount    = pedido.total,
        )
    }
}
