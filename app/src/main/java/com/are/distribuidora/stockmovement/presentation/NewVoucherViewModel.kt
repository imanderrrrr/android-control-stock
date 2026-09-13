package com.are.distribuidora.stockmovement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.FindProductByBarcodeUseCase
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de la pantalla "Nuevo vale". */
data class NewVoucherUiState(
    val type: MovementType = MovementType.ENTRADA,
    val product: Product? = null,
    val searchResults: List<Product> = emptyList(),
    val reason: MovementReason = MovementReason.COMPRA,
    val isSaving: Boolean = false,
)

sealed interface NewVoucherEvent {
    data class Saved(val movement: StockMovement) : NewVoucherEvent
    data class Error(val message: String) : NewVoucherEvent
    data class ProductNotFound(val barcode: String) : NewVoucherEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewVoucherViewModel @Inject constructor(
    private val createStockVoucher: CreateStockVoucherUseCase,
    private val findProductByBarcode: FindProductByBarcodeUseCase,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewVoucherUiState())
    val uiState: StateFlow<NewVoucherUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<NewVoucherEvent>()
    val events: SharedFlow<NewVoucherEvent> = _events.asSharedFlow()

    private val query = MutableStateFlow("")

    init {
        query
            .debounce(250)
            .distinctUntilChanged()
            .onEach { q ->
                val results = if (q.trim().length < 2) emptyList() else runCatching {
                    productRepository.searchByName(q.trim(), limit = 25)
                }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(searchResults = results)
            }
            .launchIn(viewModelScope)
    }

    /** Prefija tipo y producto (p. ej. al llegar desde el escáner de "Agregar stock"). */
    fun preset(type: MovementType?, productId: String?) {
        type?.let { setType(it) }
        if (!productId.isNullOrBlank()) {
            viewModelScope.launch {
                productRepository.getById(ProductId.of(productId))?.let { selectProduct(it) }
            }
        }
    }

    fun setType(type: MovementType) {
        val current = _uiState.value
        // Al cambiar el sentido, el motivo por defecto acompaña (entrada=Compra, salida=Merma).
        val reason = when {
            current.type == type -> current.reason
            type == MovementType.ENTRADA -> MovementReason.COMPRA
            else -> MovementReason.MERMA
        }
        _uiState.value = current.copy(type = type, reason = reason)
    }

    fun setReason(reason: MovementReason) {
        if (reason.isAutomatic) return
        _uiState.value = _uiState.value.copy(reason = reason)
    }

    fun onSearchQueryChanged(text: String) {
        query.value = text
    }

    fun selectProduct(product: Product) {
        _uiState.value = _uiState.value.copy(product = product, searchResults = emptyList())
        query.value = ""
    }

    fun clearProduct() {
        _uiState.value = _uiState.value.copy(product = null)
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val product = runCatching { findProductByBarcode(barcode) }.getOrNull()
            if (product != null) selectProduct(product) else _events.emit(NewVoucherEvent.ProductNotFound(barcode))
        }
    }

    fun submit(quantityText: String, note: String?) {
        val state = _uiState.value
        if (state.isSaving) return
        val product = state.product ?: run {
            viewModelScope.launch { _events.emit(NewVoucherEvent.Error("PRODUCT")) }
            return
        }
        val quantity = quantityText.trim().toIntOrNull()
        if (quantity == null || quantity <= 0) {
            viewModelScope.launch { _events.emit(NewVoucherEvent.Error("QUANTITY")) }
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val result = createStockVoucher(
                productId = product.id.value,
                type = state.type,
                quantity = quantity,
                reason = state.reason,
                note = note,
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
            when (result) {
                is Result.Success -> _events.emit(NewVoucherEvent.Saved(result.value))
                is Result.Error -> _events.emit(
                    NewVoucherEvent.Error(
                        when (val f = result.failure) {
                            is Failure.ValidationError -> f.message
                            Failure.NotFound -> "PRODUCT"
                            else -> "GENERIC"
                        }
                    )
                )
            }
        }
    }
}
