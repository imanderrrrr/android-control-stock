package com.are.distribuidora.pedido.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.usecase.CreatePedidoUseCase
import com.are.distribuidora.pedido.presentation.create.CartItem
import com.are.distribuidora.workers.PedidoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de confirmación del carrito.
 *
 * Responsabilidades:
 * - Validar que existan los datos necesarios (vendedorId, routeId, clienteSelection, items).
 * - Delegar la creación del pedido al [CreatePedidoUseCase].
 * - Encolar el worker de sync via [PedidoSyncScheduler] tras la creación exitosa.
 * - Exponer [UiState] a la UI para manejar Loading / Success / Error.
 */
@HiltViewModel
class OrderCartViewModel @Inject constructor(
    private val createPedidoUseCase: CreatePedidoUseCase,
    private val authRepository: AuthRepository,
    private val pedidoSyncScheduler: PedidoSyncScheduler,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val pedidoId: String) : UiState()
        data class Error(val message: String) : UiState()
        /**
         * Ya existe un pedido activo con el mismo (ruta, fecha, cliente, vendedor).
         * La UI puede ofrecer navegar al pedido existente.
         */
        data class DuplicateOrder(val existingOrderId: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Confirma el pedido actual del carrito.
     *
     * @param cartItems    ítems del carrito (del FlowViewModel).
     * @param routeId      ID de la ruta seleccionada.
     * @param cliente      Selección del cliente (existente o temporal).
     * @param descuentoGlobal descuento global aplicado al pedido (monto absoluto).
     */
    fun submitOrder(
        cartItems: List<CartItem>,
        routeId: String?,
        cliente: ClienteSelection?,
        deliveryDate: String = "",
        descuentoGlobal: Double = 0.0,
    ) {
        if (_uiState.value is UiState.Loading) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Validaciones previas
            if (cartItems.isEmpty()) {
                _uiState.value = UiState.Error("El carrito está vacío")
                return@launch
            }
            if (routeId.isNullOrBlank()) {
                _uiState.value = UiState.Error("No hay ruta seleccionada")
                return@launch
            }
            if (cliente == null) {
                _uiState.value = UiState.Error("No hay cliente seleccionado")
                return@launch
            }

            val session = authRepository.getCurrentSession()
            if (session == null) {
                _uiState.value = UiState.Error("Sesión no disponible. Por favor inicia sesión nuevamente.")
                return@launch
            }

            val itemInputs = cartItems.map { cartItem ->
                CreatePedidoItemInput(
                    productoId     = cartItem.productId,
                    nombre         = cartItem.name,
                    precioUnitario = cartItem.priceAmount,
                    cantidad       = cartItem.quantity,
                    descuentoItem  = cartItem.discountAmount,
                    notes          = cartItem.notes?.takeIf { it.isNotBlank() },
                )
            }

            when (val result = createPedidoUseCase(
                vendedorId     = session.userId,
                routeId        = routeId,
                deliveryDate   = deliveryDate,
                cliente        = cliente,
                descuentoGlobal = descuentoGlobal,
                items          = itemInputs,
            )) {
                is Result.Success -> {
                    // Encolar sync one-time si hay internet disponible
                    pedidoSyncScheduler.scheduleOneTimeSync("PEDIDO_CONFIRMED")
                    _uiState.value = UiState.Success(result.value)
                }
                is Result.Error -> {
                    when (val failure = result.failure) {
                        is Failure.DuplicateOrder ->
                            _uiState.value = UiState.DuplicateOrder(failure.existingOrderId)
                        else ->
                            _uiState.value = UiState.Error(result.failure.toString())
                    }
                }
            }
        }
    }

    /** Restablece el estado a Idle después de manejar Success/Error en la UI. */
    fun resetState() {
        _uiState.value = UiState.Idle
    }
}

