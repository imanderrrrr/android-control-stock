package com.are.distribuidora.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.AddStockToProductUseCase
import com.are.distribuidora.domain.product.FindProductByBarcodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado UI del escáner / flujo de agregar stock */
sealed interface AddStockUiState {
    object Idle : AddStockUiState
    object Scanning : AddStockUiState
    object Loading : AddStockUiState
    data class ProductFound(val product: Product, val barcode: String) : AddStockUiState
    data class ProductNotFound(val barcode: String) : AddStockUiState
    object StockUpdated : AddStockUiState
    data class Error(val message: String) : AddStockUiState
}

/** Eventos one-shot para la UI */
sealed interface AddStockEvent {
    data class StockAddedSuccess(val productName: String, val delta: Int) : AddStockEvent
    data class ShowError(val message: String) : AddStockEvent
}

@HiltViewModel
class AddStockViewModel @Inject constructor(
    private val findProductByBarcodeUseCase: FindProductByBarcodeUseCase,
    private val addStockToProductUseCase: AddStockToProductUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddStockUiState>(AddStockUiState.Scanning)
    val uiState: StateFlow<AddStockUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddStockEvent>()
    val events: SharedFlow<AddStockEvent> = _events.asSharedFlow()

    /** Evita dobles escaneos */
    private var isProcessingBarcode = false

    /**
     * Llamado cuando el escáner detecta un código de barras.
     * Debounce: si ya está procesando, ignora.
     */
    fun onBarcodeScanned(barcode: String) {
        if (isProcessingBarcode) return
        isProcessingBarcode = true

        viewModelScope.launch {
            _uiState.value = AddStockUiState.Loading
            try {
                val product = findProductByBarcodeUseCase(barcode)
                if (product != null) {
                    _uiState.value = AddStockUiState.ProductFound(product, barcode)
                } else {
                    _uiState.value = AddStockUiState.ProductNotFound(barcode)
                }
            } catch (e: Exception) {
                _uiState.value = AddStockUiState.Error(e.message ?: "Error desconocido")
                isProcessingBarcode = false
            }
        }
    }

    /**
     * Confirma el agregado de stock tras la validación en el diálogo.
     */
    fun addStock(productId: String, productName: String, delta: Int) {
        if (delta <= 0) {
            viewModelScope.launch {
                _events.emit(AddStockEvent.ShowError("La cantidad debe ser mayor a 0"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = AddStockUiState.Loading
            try {
                addStockToProductUseCase(productId, delta)
                _uiState.value = AddStockUiState.StockUpdated
                _events.emit(AddStockEvent.StockAddedSuccess(productName, delta))
            } catch (e: Exception) {
                _uiState.value = AddStockUiState.Error(e.message ?: "Error al actualizar stock")
                _events.emit(AddStockEvent.ShowError(e.message ?: "Error al actualizar stock"))
                isProcessingBarcode = false
            }
        }
    }

    /** Permite reintentar el escaneo después de un "no encontrado" o error */
    fun resumeScanning() {
        isProcessingBarcode = false
        _uiState.value = AddStockUiState.Scanning
    }
}

