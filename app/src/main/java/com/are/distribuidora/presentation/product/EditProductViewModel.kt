package com.are.distribuidora.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.images.ProductImageUrl
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.product.SaveProductUseCase
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val saveProductUseCase: SaveProductUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditProductUiState>(EditProductUiState.Loading)
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var currentProduct: Product? = null

    private val _localImageAbsolutePath = MutableStateFlow<String?>(null)
    val localImageAbsolutePath: StateFlow<String?> = _localImageAbsolutePath.asStateFlow()

    fun onImageSavedToInternalStorage(absolutePath: String) {
        _localImageAbsolutePath.value = absolutePath
    }

    fun load(productId: String) {
        viewModelScope.launch {
            _uiState.value = EditProductUiState.Loading
            try {
                val product = productRepository.getById(ProductId.of(productId))
                if (product != null) {
                    currentProduct = product
                    _uiState.value = EditProductUiState.Success(product)
                } else {
                    _uiState.value = EditProductUiState.Error("Producto no encontrado")
                }
            } catch (e: Exception) {
                _uiState.value = EditProductUiState.Error("Error al cargar producto: ${e.message}")
            }
        }
    }

    fun save(
        name: String,
        description: String?,
        category: String?,
        priceText: String,
        stockText: String,
        barcode: String?,
        isActive: Boolean
    ) {
        if (_isSaving.value) return

        val product = currentProduct ?: return

        val hasRemoteImage = ProductImageUrl.isRemoteHttp(product.imageUrl)
        val hasLocalSelection = !_localImageAbsolutePath.value.isNullOrBlank()
        val hasAnyImage = hasRemoteImage || ProductImageUrl.isLocal(product.imageUrl) || hasLocalSelection

        // Regla estricta:
        // - Si ya existe imagen remota (http/https): NO exigir nueva.
        // - Si NO hay imagen: exigirla.
        if (!hasAnyImage) {
            sendEvent(Event.Error("La imagen es obligatoria"))
            return
        }

        // Validaciones básicas
        if (name.isBlank()) {
            sendEvent(Event.Error("El nombre es obligatorio"))
            return
        }

        val price = try {
            val priceValue = priceText.trim().toBigDecimalOrNull()
            if (priceValue == null || priceValue < BigDecimal.ZERO) {
                sendEvent(Event.Error("El precio debe ser un número válido mayor o igual a 0"))
                return
            }
            Money.of(priceValue)
        } catch (e: Exception) {
            sendEvent(Event.Error("Precio inválido: ${e.message}"))
            return
        }

        val stock = try {
            val stockValue = stockText.trim().toIntOrNull()
            if (stockValue == null || stockValue < 0) {
                sendEvent(Event.Error("El stock debe ser un número entero mayor o igual a 0"))
                return
            }
            Quantity.of(stockValue)
        } catch (e: Exception) {
            sendEvent(Event.Error("Stock inválido: ${e.message}"))
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            try {
                val nextImageUrl = if (hasLocalSelection) {
                    ProductImageUrl.toLocalUrl(_localImageAbsolutePath.value!!)
                } else {
                    product.imageUrl
                }

                // Construir producto actualizado preservando ID, createdAt y otros campos inmutables
                val updatedProduct = product.copy(
                    name = name.trim(),
                    description = if (description.isNullOrBlank()) null else description.trim(),
                    category = if (category.isNullOrBlank()) null else category.trim(),
                    price = price,
                    stock = stock,
                    barcode = if (barcode.isNullOrBlank()) null else barcode.trim(),
                    isActive = isActive,
                    imageUrl = nextImageUrl,
                    updatedAt = System.currentTimeMillis()
                )

                saveProductUseCase(updatedProduct)
                // Reset selección local (opcional) tras guardar
                _localImageAbsolutePath.value = null
                sendEvent(Event.Success)
            } catch (e: Exception) {
                sendEvent(Event.Error("Error al guardar: ${e.message}"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    sealed class EditProductUiState {
        object Loading : EditProductUiState()
        data class Success(val product: Product) : EditProductUiState()
        data class Error(val message: String) : EditProductUiState()
    }

    sealed class Event {
        object Success : Event()
        data class Error(val message: String) : Event()
    }
}
