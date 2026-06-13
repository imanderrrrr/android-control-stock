package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ObserveProductSyncStatusesUseCase
import com.are.distribuidora.domain.product.ObserveProductsUseCase
import com.are.distribuidora.domain.sale.SellProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

// UI State simplificado, Paging maneja su propio estado de carga/lista
data class InventoryUiState(
    // val products: Flow<PagingData<ProductUiModel>> // Mejor expuesto directo en VM
    val isLoading: Boolean = false, // Placeholder, usar LoadState de Paging en UI
)

sealed interface InventoryEvent {
    data class SaleSuccess(val productId: String, val newStock: Int) : InventoryEvent
    data class SaleError(val productId: String, val message: String) : InventoryEvent
    data class StockAddedSuccess(val productId: String, val delta: Int) : InventoryEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val observeProductsUseCase: ObserveProductsUseCase,
    observeProductSyncStatusesUseCase: ObserveProductSyncStatusesUseCase,
    private val sellProductUseCase: SellProductUseCase,
    private val deleteProductUseCase: com.are.distribuidora.domain.product.DeleteProductUseCase,
) : ViewModel() {

    // Query de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Stream paginado de productos (dominio puro).
     *
     * ÚNICA fuente de `submitData()` en la UI. El estado de sincronización NO
     * viaja dentro del PagingData: llega por [syncStatuses], un canal separado
     * que el adapter aplica con `notifyItemChanged` puntual.
     *
     * Esto evita la race "Inconsistency detected. Invalid view holder adapter
     * position" del RecyclerView, que ocurría cuando un segundo flujo (los
     * estados de sync) re-disparaba `submitData` a mitad del layout pass — el
     * mismo bug ya corregido en OrderCatalog (commit 500e7bb), portado aquí.
     *
     * `cachedIn` preserva la generación del Pager de Room entre recreaciones de vista.
     */
    val products: Flow<PagingData<Product>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            observeProductsUseCase(query)
        }
        .cachedIn(viewModelScope)

    /**
     * Estados de sincronización por productId (canal lateral, fuera del PagingData).
     * El adapter los aplica vía `submitSyncStatuses()` sin re-disparar `submitData`.
     */
    val syncStatuses: Flow<Map<String, SyncState>> = observeProductSyncStatusesUseCase()

    // UI State para otros estados (si fuera necesario)
    // Por ahora Paging se maneja con 'products.collectAsLazyPagingItems()' en UI

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
                // TODO: Update sell logic to handle ID correctly if needed, but here we just pass ID.
                // Note: The UI now binds ProductUiModel, but the click listener might still pass Product or we just use ID.
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

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                // Delegation to UseCase -> Repository -> DAO (soft delete)
                // The repository handles marking logic: isDeleted=1, syncStatus=PENDING_DELETE
                deleteProductUseCase(productId)
                android.util.Log.d("InventoryViewModel", "Product deleted: $productId")
            } catch (e: Exception) {
                android.util.Log.e("InventoryViewModel", "Error deleting product: $productId", e)
                // Optional: Emit error event if needed
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
