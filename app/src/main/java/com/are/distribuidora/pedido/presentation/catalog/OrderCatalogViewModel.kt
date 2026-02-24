package com.are.distribuidora.pedido.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ObserveProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OrderCatalogViewModel @Inject constructor(
    private val observeProductsUseCase: ObserveProductsUseCase,
) : ViewModel() {

    // ── Filtro de búsqueda por texto ──────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── Filtro de categoría (presentation only) ───────────────────────────────
    private val _selectedCategory = MutableStateFlow(CategoryFilter.TODOS)
    val selectedCategory: StateFlow<CategoryFilter> = _selectedCategory.asStateFlow()

    // ── Stream de productos: busca por query en data, filtra por categoría en presentation ──
    // El PagingSource de Room ya filtra por nombre/categoría con LIKE.
    // Adicionalmente aplicamos PagingData.filter para el chip seleccionado,
    // garantizando que el resultado sea exacto sin tocar data/domain.
    val products: Flow<PagingData<Product>> =
        combine(_searchQuery.debounce(300).distinctUntilChanged(), _selectedCategory) { query, category ->
            Pair(query, category)
        }
        .flatMapLatest { (query, category) ->
            observeProductsUseCase(query)
                .map { pagingData ->
                    pagingData.filter { product -> category.matches(product.category) }
                }
        }
        .cachedIn(viewModelScope)

    // ── Acciones ──────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: CategoryFilter) {
        _selectedCategory.value = category
    }
}
