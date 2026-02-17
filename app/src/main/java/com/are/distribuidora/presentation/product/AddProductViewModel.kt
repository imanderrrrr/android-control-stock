package com.are.distribuidora.presentation.product

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.images.ProductImageUrl
import com.are.distribuidora.domain.model.Product
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val saveProductUseCase: SaveProductUseCase
) : ViewModel() {

    // ID fijo para este flujo de creación (se mantiene mientras viva el VM)
    private val newProductId: String = UUID.randomUUID().toString()

    fun getNewProductId(): String = newProductId

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _localImageAbsolutePath = MutableStateFlow<String?>(null)
    val localImageAbsolutePath: StateFlow<String?> = _localImageAbsolutePath.asStateFlow()

    fun onImageSavedToInternalStorage(absolutePath: String) {
        _localImageAbsolutePath.value = absolutePath
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

        val localPath = _localImageAbsolutePath.value
        if (localPath.isNullOrBlank()) {
            sendEvent(Event.Error("La imagen es obligatoria"))
            return
        }

        // Validaciones básicas (idénticas a Edit)
        if (name.isBlank()) {
            sendEvent(Event.Error("El nombre es obligatorio"))
            return
        }

        val price = try {
            // Nota: mantenemos el mismo parseo que edición para consistencia.
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
                val now = System.currentTimeMillis()
                val newProduct = Product(
                    id = ProductId.of(newProductId),
                    name = name.trim(),
                    description = if (description.isNullOrBlank()) null else description.trim(),
                    category = if (category.isNullOrBlank()) null else category.trim(),
                    price = price,
                    imageUrl = ProductImageUrl.toLocalUrl(localPath),
                    barcode = if (barcode.isNullOrBlank()) null else barcode.trim(),
                    stock = stock,
                    isActive = isActive,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now
                )

                Log.i(TAG, "Saving new product id=${newProduct.id.value} name=${newProduct.name}")
                saveProductUseCase(newProduct)

                // Importante: el repo decide CREATE vs UPDATE y setea SyncStatus.
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

    sealed class Event {
        object Success : Event()
        data class Error(val message: String) : Event()
    }

    private companion object {
        private const val TAG = "AddProduct"
    }
}
