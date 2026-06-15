package com.are.distribuidora.pedido.presentation.editotros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.orders.domain.usecase.EditOtrosOrderUseCase
import com.are.distribuidora.orders.domain.usecase.GetOtrosPedidoDetalleUseCase
import com.are.distribuidora.workers.OrdersUploadScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel para EDITAR un pedido ajeno ("Otros Pedidos").
 *
 * Opera sobre el sistema `orders` (no sobre `pedidos`). El guardado:
 *  - reemplaza el set de ítems en Room (incluye ítems personalizados `custom_<UUID>`),
 *  - marca el pedido pendiente de subir SIN cambiar al vendedor original,
 *  - encola [OrdersUploadScheduler] para subir la edición a Firestore.
 *
 * Fuente única de verdad para la lista: [_items] (Map por localKey) → UiState.Editing.items.
 * Todas las mutaciones reconstruyen el estado; nunca hay notify lateral al RecyclerView
 * (se evita el crash "Inconsistency detected" por doble fuente).
 *
 * Scope: `activityViewModels()` para compartirse con el catálogo de selección de productos.
 */
@HiltViewModel
class EditOtrosPedidoViewModel @Inject constructor(
    private val getDetalle: GetOtrosPedidoDetalleUseCase,
    private val editOtrosOrderUseCase: EditOtrosOrderUseCase,
    private val ordersUploadScheduler: OrdersUploadScheduler,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Editing(
            val clientName: String,
            val items: List<EditOtrosItemUiModel>,
            val totalFormatted: String,
        ) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class SaveEvent {
        object Success : SaveEvent()
        data class Error(val message: String) : SaveEvent()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _saveEvent = MutableSharedFlow<SaveEvent>()
    val saveEvent: SharedFlow<SaveEvent> = _saveEvent.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Ítems en edición: clave = productId (único por pedido). Fuente única para el adapter. */
    private val _items = MutableStateFlow<Map<String, EditOtrosItemUiModel>>(emptyMap())

    private var currentOrderId: String = ""
    private var clientName: String = ""

    /** Pre-poblamos desde Room una sola vez por sesión (sobrevive ir al catálogo y volver). */
    private var hasInitialized = false

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }

    fun init(orderId: String) {
        if (currentOrderId != orderId) {
            currentOrderId = orderId
            clientName     = ""
            _items.value   = emptyMap()
            hasInitialized = false
            _uiState.value = UiState.Loading
        }

        // Volviendo del catálogo: preservar la edición en curso, no recargar.
        if (hasInitialized) {
            rebuild()
            return
        }

        viewModelScope.launch {
            try {
                val result = getDetalle(orderId)
                val order = result.order
                if (order == null) {
                    _uiState.value = UiState.Error("Pedido no encontrado")
                    return@launch
                }
                clientName = order.clientName
                val initial = result.items.associate { item ->
                    item.productId to EditOtrosItemUiModel(
                        localKey       = item.productId,
                        itemId         = item.itemId.ifBlank { "item_${UUID.randomUUID()}" },
                        productId      = item.productId,
                        nombre         = item.productName,
                        precioUnitario = item.unitPrice,
                        cantidad       = item.quantity,
                        notes          = item.notes,
                    )
                }
                _items.value = initial
                hasInitialized = true
                rebuild()
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error al cargar el pedido")
            }
        }
    }

    /** Agrega un producto de catálogo. Si ya está en el pedido, incrementa su cantidad. */
    fun addProduct(product: Product) {
        val productId = product.id.value
        val current = _items.value.toMutableMap()
        val existing = current[productId]
        if (existing != null) {
            current[productId] = existing.copy(cantidad = existing.cantidad + 1)
        } else {
            current[productId] = EditOtrosItemUiModel(
                localKey       = productId,
                itemId         = "item_${UUID.randomUUID()}",
                productId      = productId,
                nombre         = product.name,
                precioUnitario = product.price.amount.toDouble(),
                cantidad       = 1,
                notes          = null,
            )
        }
        _items.value = current
        rebuild()
    }

    /** Agrega un ítem personalizado (sin producto de catálogo): productId `custom_<UUID>`. */
    fun addCustomItem(name: String, quantity: Int, price: Double, notes: String?) {
        if (name.isBlank() || quantity <= 0 || price < 0.0) return
        val customId = "custom_${UUID.randomUUID()}"
        val current = _items.value.toMutableMap()
        current[customId] = EditOtrosItemUiModel(
            localKey       = customId,
            itemId         = customId,
            productId      = customId,
            nombre         = name.trim(),
            precioUnitario = price,
            cantidad       = quantity,
            notes          = notes?.takeIf { it.isNotBlank() },
        )
        _items.value = current
        rebuild()
    }

    fun increment(localKey: String) {
        val current = _items.value.toMutableMap()
        val item = current[localKey] ?: return
        current[localKey] = item.copy(cantidad = item.cantidad + 1)
        _items.value = current
        rebuild()
    }

    /** Decrementa; si llega a 0, quita el ítem del pedido. */
    fun decrement(localKey: String) {
        val current = _items.value.toMutableMap()
        val item = current[localKey] ?: return
        if (item.cantidad <= 1) {
            current.remove(localKey)
        } else {
            current[localKey] = item.copy(cantidad = item.cantidad - 1)
        }
        _items.value = current
        rebuild()
    }

    fun removeItem(localKey: String) {
        val current = _items.value.toMutableMap()
        current.remove(localKey)
        _items.value = current
        rebuild()
    }

    fun save() {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true

            val inputs = _items.value.values.map { m ->
                EditOrderItemInput(
                    itemId      = m.itemId,
                    productId   = m.productId,
                    productName = m.nombre,
                    unitPrice   = m.precioUnitario,
                    quantity    = m.cantidad,
                    notes       = m.notes,
                )
            }

            when (val result = editOtrosOrderUseCase(orderId = currentOrderId, items = inputs)) {
                is Result.Success -> {
                    ordersUploadScheduler.enqueueUploadWorker(reason = "ORDER_EDITED")
                    // Reset para que una próxima edición recargue fresco desde Room.
                    resetState()
                    _saveEvent.emit(SaveEvent.Success)
                }
                is Result.Error -> {
                    val msg = when (val f = result.failure) {
                        is Failure.ValidationError -> f.message
                        Failure.NotFound -> "El pedido ya no existe"
                        else -> "No se pudieron guardar los cambios"
                    }
                    _saveEvent.emit(SaveEvent.Error(msg))
                }
            }
            _isSaving.value = false
        }
    }

    private fun resetState() {
        currentOrderId = ""
        clientName     = ""
        _items.value   = emptyMap()
        hasInitialized = false
    }

    private fun rebuild() {
        val items = _items.value.values.sortedBy { it.nombre.lowercase() }
        val total = RoundToQuarterQuetzalUseCase(items.sumOf { it.precioUnitario * it.cantidad })
        _uiState.value = UiState.Editing(
            clientName     = clientName,
            items          = items,
            totalFormatted = currencyFormat.format(total),
        )
    }
}

/** Modelo de un ítem en el carrito de edición de un pedido ajeno. */
data class EditOtrosItemUiModel(
    val localKey: String,
    val itemId: String,
    val productId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val notes: String? = null,
) {
    val subtotal: Double get() = precioUnitario * cantidad
}
