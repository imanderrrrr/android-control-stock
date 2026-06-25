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
import java.util.concurrent.atomic.AtomicBoolean
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
        /**
         * El total del pedido excede el límite de compra configurado para el cliente.
         */
        data class OrderLimitExceeded(
            val limitInCents: Long,
            val totalInCents: Long,
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Guard atómico que previene ejecuciones concurrentes de [submitOrder].
     *
     * Problema que resuelve: un doble-tap rápido en el botón "Confirmar" podía lanzar
     * dos coroutines casi simultáneamente. El chequeo simple `_uiState.value is Loading`
     * NO es atómico — ambas corutinas podían leerlo como `Idle` antes de que alguna
     * cambiara el estado, creando dos pedidos duplicados en Room.
     *
     * Solución: [AtomicBoolean.compareAndSet] es una operación atómica a nivel de CPU.
     * Solo la primera llamada que llegue lo cambia de `false → true` y puede continuar;
     * cualquier llamada posterior encuentra `true` y retorna de inmediato.
     * El bloque `finally` garantiza que siempre se libere, incluso si hay una excepción.
     */
    private val isSubmitting = AtomicBoolean(false)

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
        applyIva: Boolean = false,
    ) {
        // compareAndSet(false, true) es atómico: retorna `true` solo si el valor era `false`
        // y lo cambia a `true` en una sola instrucción de CPU — sin race condition posible.
        if (!isSubmitting.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
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
                    vendedorId      = session.userId,
                    routeId         = routeId,
                    deliveryDate    = deliveryDate,
                    cliente         = cliente,
                    descuentoGlobal = descuentoGlobal,
                    items           = itemInputs,
                    applyIva        = applyIva,
                )) {
                    is Result.Success -> {
                        pedidoSyncScheduler.scheduleOneTimeSync("PEDIDO_CONFIRMED")
                        _uiState.value = UiState.Success(result.value)
                    }
                    is Result.Error -> {
                        when (val failure = result.failure) {
                            is Failure.DuplicateOrder ->
                                _uiState.value = UiState.DuplicateOrder(failure.existingOrderId)
                            is Failure.OrderLimitExceeded ->
                                _uiState.value = UiState.OrderLimitExceeded(
                                    limitInCents = failure.limitInCents,
                                    totalInCents = failure.totalInCents,
                                )
                            else ->
                                _uiState.value = UiState.Error(result.failure.toString())
                        }
                    }
                }
            } finally {
                // Siempre libera el guard, incluso si hubo una excepción no capturada.
                isSubmitting.set(false)
            }
        }
    }

    /** Restablece el estado a Idle después de manejar Success/Error en la UI. */
    fun resetState() {
        _uiState.value = UiState.Idle
    }
}

