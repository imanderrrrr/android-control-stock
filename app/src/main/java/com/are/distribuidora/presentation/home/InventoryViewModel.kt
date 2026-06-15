package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.GetProductCountUseCase
import com.are.distribuidora.domain.product.ObserveProductSyncStatusesUseCase
import com.are.distribuidora.domain.product.ObserveProductsUseCase
import com.are.distribuidora.domain.sale.SellProductUseCase
import com.are.distribuidora.presentation.home.mapper.toUiModel
import com.are.distribuidora.presentation.home.model.ProductUiModel
import com.are.distribuidora.workers.ProductSyncScheduler
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
import kotlinx.coroutines.flow.combine
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
    private val getProductCountUseCase: GetProductCountUseCase,
    observeProductSyncStatusesUseCase: ObserveProductSyncStatusesUseCase,
    private val sellProductUseCase: SellProductUseCase,
    private val deleteProductUseCase: com.are.distribuidora.domain.product.DeleteProductUseCase,
    private val productSyncScheduler: ProductSyncScheduler,
) : ViewModel() {

    init {
        // Carga proactiva del catálogo al abrir Inventario.
        //
        // La descarga de productos (downstream) NO estaba acoplada a la apertura
        // de la pantalla: sólo corría con el worker periódico (6h) o cuando el
        // ProductSyncCoordinator detectaba cambios locales PENDIENTES de subir
        // (exige countPending > 0). En un dispositivo recién instalado —sin
        // productos ni pendientes— nada disparaba la PRIMERA descarga, y el
        // inventario quedaba vacío hasta que el usuario creaba/editaba un producto
        // (lo que daba un pendiente y, de rebote, traía todo el catálogo).
        //
        // Encolamos un sync puntual al entrar para bajar el catálogo de Firestore
        // de inmediato. Seguro de repetir en cada apertura: el worker valida
        // red/sesión, corre con mutex (descarta si ya hay uno activo) y deduplica
        // por uniqueName (REPLACE).
        productSyncScheduler.scheduleOneTimeNow("INVENTORY_OPEN")
    }

    // Query de búsqueda
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Stream paginado base (dominio puro), cacheado en el scope del ViewModel.
     *
     * `cachedIn` va ANTES del `combine` de [products] a propósito: así las
     * emisiones del flujo de estados de sync re-mapean las páginas YA cargadas
     * sin volver a consultar el PagingSource ni recrear la generación del Pager
     * (y preserva la posición de scroll entre recreaciones de vista).
     */
    private val pagedProducts: Flow<PagingData<Product>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            observeProductsUseCase(query)
        }
        .cachedIn(viewModelScope)

    /**
     * ÚNICA fuente de `submitData()` de la UI: el producto paginado YA lleva su
     * estado de sincronización fusionado vía [combine] + [PagingData.map].
     *
     * Antes el estado de sync viajaba por un canal lateral que hacía
     * `notifyItemChanged` manual sobre el PagingDataAdapter. Como el PagingSource
     * de productos y el flujo de sync observan la MISMA tabla `products`, cada
     * sincronización los invalidaba a la vez: el `submitData` (refresh
     * estructural del differ) y el `notifyItemChanged` manual corrían
     * concurrentes y corrompían el bookkeeping del RecyclerView →
     * "Inconsistency detected. Invalid view holder adapter position".
     *
     * Al fusionar el sync DENTRO del único stream paginado, el differ gestiona
     * todo por un solo canal y la race desaparece. Los cambios de solo-sync se
     * aplican como bind parcial (PAYLOAD_SYNC) vía `getChangePayload` del
     * DiffUtil del adapter, sin recargar la imagen.
     *
     * (En OrderCatalog el canal lateral SÍ es seguro porque lo dispara el
     * usuario —cantidades del carrito— y nunca coincide con un sync.)
     */
    val products: Flow<PagingData<ProductUiModel>> = combine(
        pagedProducts,
        observeProductSyncStatusesUseCase(),
    ) { paging, statuses ->
        paging.map { product -> product.toUiModel(statuses[product.id.value]) }
    }

    // UI State para otros estados (si fuera necesario)
    // Por ahora Paging se maneja con 'products.collectAsLazyPagingItems()' en UI

    private val _events = MutableSharedFlow<InventoryEvent>()
    val events = _events.asSharedFlow()

    private val _productCount = MutableStateFlow<Int?>(null)
    val productCount: StateFlow<Int?> = _productCount.asStateFlow()

    init {
        viewModelScope.launch {
            _productCount.value = runCatching { getProductCountUseCase() }.getOrNull()
        }
    }

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
