package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ObserveProductsUseCase
import com.are.distribuidora.domain.sale.SellProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface InventoryEvent {
    data class SaleSuccess(val productId: String, val newStock: Int) : InventoryEvent
    data class SaleError(val productId: String, val message: String) : InventoryEvent
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    observeProductsUseCase: ObserveProductsUseCase,
    private val sellProductUseCase: SellProductUseCase,
) : ViewModel() {

    /**
     * Stream reactivo de productos (fuente de verdad), proveniente de un UseCase.
     * No hay load manual.
     */
    val products: StateFlow<List<Product>> = observeProductsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // Query actual de búsqueda (estado UI). Se actualiza desde la UI.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Productos filtrados en memoria (case-insensitive) a partir de la lista ya observada.
    private val filteredProducts: StateFlow<List<Product>> = combine(
        products,
        searchQuery,
    ) { products, query ->
        val q = query.trim()
        if (q.isEmpty()) {
            products
        } else {
            val qLower = q.lowercase()
            products.filter { p ->
                p.name.lowercase().contains(qLower)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val uiState: StateFlow<InventoryUiState> = filteredProducts
        .map { items ->
            InventoryUiState(
                products = items,
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InventoryUiState(isLoading = true),
        )

    private val _events = MutableSharedFlow<InventoryEvent>()
    val events = _events.asSharedFlow()

    /**
     * Acción (placeholder) para ejecutar una venta desde UI.
     *
     * Regla: NO se recalcula ni se resta stock aquí. Solo se delega a dominio (SellProductUseCase).
     */
    fun confirmSale(productId: String, quantity: Int) {
        viewModelScope.launch {
            try {
                val updated = sellProductUseCase.execute(productId = productId, quantity = quantity)
                _events.emit(InventoryEvent.SaleSuccess(productId = productId, newStock = updated.stock.value))
            } catch (t: Throwable) {
                _events.emit(
                    InventoryEvent.SaleError(
                        productId = productId,
                        message = t.message ?: "Error desconocido",
                    ),
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
