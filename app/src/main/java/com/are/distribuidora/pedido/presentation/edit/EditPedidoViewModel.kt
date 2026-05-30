package com.are.distribuidora.pedido.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.model.EditPedidoItemInput
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.PreviousItemSnapshot
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountByAmountUseCase
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.domain.pedido.usecase.EditPedidoUseCase
import com.are.distribuidora.domain.pedido.usecase.ObserveAllPedidosUseCase
import com.are.distribuidora.domain.pedido.usecase.RoundToQuarterQuetzalUseCase
import com.are.distribuidora.workers.PedidoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel para la pantalla de edición de pedido.
 *
 * Mantiene un "edit cart" local aislado del carrito de creación de pedidos.
 * - Carga los ítems actuales del pedido al iniciarse.
 * - Permite agregar, modificar cantidad, cambiar descuento y eliminar ítems.
 * - Al guardar, llama a [EditPedidoUseCase] que escribe en Room de forma atómica
 *   y marca el pedido como PENDING_UPDATE.
 * - Encola el sync worker tras el guardado exitoso.
 */
@HiltViewModel
class EditPedidoViewModel @Inject constructor(
    private val observeAllPedidosUseCase: ObserveAllPedidosUseCase,
    private val editPedidoUseCase: EditPedidoUseCase,
    private val authRepository: AuthRepository,
    private val pedidoSyncScheduler: PedidoSyncScheduler,
    private val productDao: ProductDao,
    private val applyItemDiscountUseCase: ApplyItemDiscountUseCase,
    private val applyItemDiscountByAmountUseCase: ApplyItemDiscountByAmountUseCase,
) : ViewModel() {

    // ── Estados de UI ────────────────────────────────────────────────────────

    sealed class UiState {
        object Loading : UiState()
        data class Editing(
            val clienteNombre: String,
            val items: List<EditItemUiModel>,
            val totalFormatted: String,
            val subtotalFormatted: String,
            val descuentoGlobal: Double,
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

    // ── Estado interno del carrito de edición ────────────────────────────────

    /** Ítems en edición: mapa itemId → EditItemUiModel (clave uuid temporal para nuevos). */
    private val _editItems = MutableStateFlow<Map<String, EditItemUiModel>>(emptyMap())
    val editItems: StateFlow<Map<String, EditItemUiModel>> = _editItems.asStateFlow()

    /** IDs de ítems marcados para eliminar (soft delete). */
    private val _deletedItemIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Snapshot de los ítems ORIGINALES del pedido (antes de cualquier edición).
     * Se captura una sola vez al cargar y se envía al repositorio para calcular
     * el delta de stock correctamente.
     */
    private var _previousItems: List<PreviousItemSnapshot> = emptyList()

    private var currentPedidoId: String = ""
    private var currentClienteId: String? = null
    private var descuentoGlobal: Double = 0.0
    private var clienteNombre: String = ""

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }

    // ── Inicialización ───────────────────────────────────────────────────────

    /**
     * Inicializa el estado de edición a partir del pedido actual en Room.
     * Si se llama con un [pedidoId] distinto al cargado actualmente, limpia
     * todo el estado previo antes de cargar el nuevo pedido.
     */
    fun init(pedidoId: String) {
        // Si es un pedido distinto al que ya está cargado, resetear todo el estado
        if (currentPedidoId != pedidoId) {
            currentPedidoId  = pedidoId
            currentClienteId = null
            descuentoGlobal  = 0.0
            clienteNombre    = ""
            _editItems.value = emptyMap()
            _deletedItemIds.value = emptySet()
            _previousItems   = emptyList()
            _uiState.value   = UiState.Loading
        }
        currentPedidoId = pedidoId
        viewModelScope.launch {
            observeAllPedidosUseCase().map { list ->
                list.firstOrNull { it.pedido.id == pedidoId }
            }.collect { pw ->
                if (pw == null) {
                    _uiState.value = UiState.Error("Pedido no encontrado")
                    return@collect
                }
                // Solo pre-poblar la primera vez (no sobreescribir ediciones en curso)
                if (_editItems.value.isEmpty() && currentPedidoId == pedidoId) {
                    clienteNombre    = pw.pedido.clienteSnapshot.nombre
                    currentClienteId = pw.pedido.clienteId
                    descuentoGlobal  = pw.pedido.descuentoGlobal
                    // Resolver imageUrl de cada ítem consultando la tabla de productos
                    val initialItems = pw.items.associate { item ->
                        val imageUrl = productDao.getById(item.productoId)?.imageUrl
                        item.id to EditItemUiModel(
                            localKey       = item.id,
                            existingItemId = item.id,
                            productoId     = item.productoId,
                            nombre         = item.nombre,
                            precioUnitario = item.precioUnitario,
                            cantidad       = item.cantidad,
                            descuentoItem  = item.descuentoItem,
                            imageUrl       = imageUrl,
                            notes          = item.notes,
                        )
                    }
                    _editItems.value = initialItems
                    // Snapshot de los ítems ANTES de editar (para calcular delta de stock)
                    _previousItems = pw.items.map { item ->
                        PreviousItemSnapshot(
                            itemId     = item.id,
                            productoId = item.productoId,
                            cantidad   = item.cantidad,
                        )
                    }
                }
                rebuildUiState()
            }
        }
    }

    // ── Operaciones del carrito de edición ───────────────────────────────────

    /** Agrega un producto nuevo al carrito de edición (qty=1, sin descuento). */
    fun addProduct(product: Product) {
        val localKey = "NEW-${product.id.value}-${System.currentTimeMillis()}"
        val current  = _editItems.value.toMutableMap()
        // _editItems solo tiene activos; si ya existe uno con este productoId, incrementa cantidad
        val existing = current.values.firstOrNull { it.productoId == product.id.value }
        if (existing != null) {
            current[existing.localKey] = existing.copy(cantidad = existing.cantidad + 1)
        } else {
            current[localKey] = EditItemUiModel(
                localKey       = localKey,
                existingItemId = null,
                productoId     = product.id.value,
                nombre         = product.name,
                precioUnitario = product.price.amount.toDouble(),
                cantidad       = 1,
                descuentoItem  = 0.0,
                imageUrl       = product.imageUrl,
            )
        }
        _editItems.value = current
        rebuildUiState()
    }

    /** Actualiza la cantidad de un ítem. Si qty == 0 → marca para eliminar. */
    fun setQuantity(localKey: String, qty: Int) {
        if (qty <= 0) {
            deleteItem(localKey)
            return
        }
        val current = _editItems.value.toMutableMap()
        val item    = current[localKey] ?: return
        current[localKey] = item.copy(cantidad = qty)
        _editItems.value = current
        rebuildUiState()
    }

    /** Actualiza el descuento por PORCENTAJE de un ítem. */
    fun setDiscount(localKey: String, percent: Double) {
        val clamped = percent.coerceIn(0.0, 100.0)
        val current = _editItems.value.toMutableMap()
        val item    = current[localKey] ?: return
        val monto   = applyItemDiscountUseCase(
            precioUnitario = item.precioUnitario,
            cantidad       = item.cantidad,
            porcentaje     = clamped,
        )
        current[localKey] = item.copy(
            descuentoItem  = monto,
            discountPercent = clamped,
            discountType   = DiscountType.PERCENTAGE,
        )
        _editItems.value = current
        rebuildUiState()
    }

    /** Actualiza el descuento por MONTO FIJO en Quetzales de un ítem. */
    fun setDiscountByAmount(localKey: String, amount: Double) {
        val current = _editItems.value.toMutableMap()
        val item    = current[localKey] ?: return
        val monto   = applyItemDiscountByAmountUseCase(
            precioUnitario = item.precioUnitario,
            cantidad       = item.cantidad,
            montoDescuento = amount.coerceAtLeast(0.0),
        )
        current[localKey] = item.copy(
            descuentoItem  = monto,
            discountPercent = 0.0,
            discountType   = DiscountType.AMOUNT,
        )
        _editItems.value = current
        rebuildUiState()
    }

    /**
     * Marca un ítem para eliminar.
     * - Si tiene existingItemId → se añade al conjunto de soft-deletes.
     * - Si es nuevo (existingItemId == null) → se quita del mapa directamente.
     */
    fun deleteItem(localKey: String) {
        val item = _editItems.value[localKey] ?: return
        if (item.existingItemId != null) {
            _deletedItemIds.value = _deletedItemIds.value + item.existingItemId
        }
        _editItems.value = _editItems.value.toMutableMap().also { it.remove(localKey) }
        rebuildUiState()
    }

    // ── Guardar ──────────────────────────────────────────────────────────────

    fun save() {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true

            val session = authRepository.getCurrentSession()
            if (session == null) {
                _saveEvent.emit(SaveEvent.Error("Sesión no disponible"))
                _isSaving.value = false
                return@launch
            }

            // _editItems solo contiene activos (deleteItem los quita del mapa)
            val activeItems = _editItems.value.values
                .map { model ->
                    EditPedidoItemInput(
                        itemId         = model.existingItemId,
                        productoId     = model.productoId,
                        nombre         = model.nombre,
                        precioUnitario = model.precioUnitario,
                        cantidad       = model.cantidad,
                        descuentoItem  = model.descuentoItem,
                        notes          = model.notes,
                    )
                }
            val params = EditPedidoParams(
                pedidoId          = currentPedidoId,
                vendedorId        = session.userId,
                clienteId         = currentClienteId,
                itemsToUpsert     = activeItems,
                itemIdsToDelete   = _deletedItemIds.value.toList(),
                previousItems     = _previousItems,
                descuentoGlobal   = descuentoGlobal,
            )

            when (val result = editPedidoUseCase(params)) {
                is Result.Success -> {
                    pedidoSyncScheduler.scheduleOneTimeSync("PEDIDO_EDITED")
                    _saveEvent.emit(SaveEvent.Success)
                }
                is Result.Error -> {
                    val msg = when (val f = result.failure) {
                        is com.are.distribuidora.core.result.Failure.ValidationError -> f.message
                        is com.are.distribuidora.core.result.Failure.OrderLimitExceeded -> {
                            val limitFormatted = f.limitInCents / 100.0
                            val totalFormatted = f.totalInCents / 100.0
                            "El pedido (Q${"%.2f".format(totalFormatted)}) excede el límite de compra de Q${"%.2f".format(limitFormatted)}"
                        }
                        else -> "Error al guardar los cambios"
                    }
                    _saveEvent.emit(SaveEvent.Error(msg))
                }
            }
            _isSaving.value = false
        }
    }

    // ── UI rebuild ───────────────────────────────────────────────────────────

    private fun rebuildUiState() {
        // _editItems solo contiene ítems activos: deleteItem() los elimina del mapa
        val activeItems = _editItems.value.values.toList()
        // Subtotal = suma de subtotales por línea YA redondeados (EditItemUiModel.subtotal).
        // Así el subtotal mostrado es exactamente la suma de los montos por línea que el
        // cliente ve en pantalla, sin centavos "fantasma".
        val subtotal = RoundToQuarterQuetzalUseCase(activeItems.sumOf { it.subtotal })
        val total = RoundToQuarterQuetzalUseCase((subtotal - descuentoGlobal).coerceAtLeast(0.0))

        _uiState.value = UiState.Editing(
            clienteNombre      = clienteNombre,
            items              = activeItems,
            totalFormatted     = currencyFormat.format(total),
            subtotalFormatted  = currencyFormat.format(subtotal),
            descuentoGlobal    = descuentoGlobal,
        )
    }
}

/**
 * Modelo de ítem en el carrito de edición.
 * [localKey] es clave única en el mapa (UUID temporal para ítems nuevos, itemId para existentes).
 * [existingItemId] es null para ítems recién añadidos que aún no tienen ID de Room.
 */
data class EditItemUiModel(
    val localKey: String,
    val existingItemId: String?,
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoItem: Double,
    val imageUrl: String? = null,
    val notes: String? = null,
    val discountPercent: Double = 0.0,
    val discountType: DiscountType = DiscountType.PERCENTAGE,
) {
    /** Subtotal sin descuento redondeado al múltiplo de Q 0.25 más cercano. */
    val subtotalBase: Double get() = RoundToQuarterQuetzalUseCase(precioUnitario * cantidad)

    /** Subtotal con descuento aplicado redondeado al múltiplo de Q 0.25 más cercano. */
    val subtotal: Double
        get() = RoundToQuarterQuetzalUseCase(
            ((precioUnitario * cantidad) - descuentoItem).coerceAtLeast(0.0)
        )

    val hasDiscount: Boolean get() = descuentoItem > 0.0
}






