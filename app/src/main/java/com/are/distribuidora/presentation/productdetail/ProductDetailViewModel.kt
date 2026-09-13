package com.are.distribuidora.presentation.productdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.product.ObserveProductByIdUseCase
import com.are.distribuidora.domain.product.ObserveProductSyncStatusesUseCase
import com.are.distribuidora.domain.valueobject.ProductId
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProductDetailViewModel @AssistedInject constructor(
    private val observeProductByIdUseCase: ObserveProductByIdUseCase,
    private val observeProductSyncStatusesUseCase: ObserveProductSyncStatusesUseCase,
    private val observeProductMovementsUseCase: com.are.distribuidora.stockmovement.domain.usecase.ObserveProductMovementsUseCase,
    @Assisted private val productId: String,
) : ViewModel() {

    val uiState: StateFlow<ProductDetailUiState> = combine(
        observeProductByIdUseCase(ProductId.of(productId)),
        observeProductSyncStatusesUseCase(),
        observeProductMovementsUseCase(productId, limit = 50),
    ) { product, statuses, movements ->
        if (product == null) {
            ProductDetailUiState.Error(ProductDetailUiState.ErrorKind.NOT_FOUND)
        } else {
            val state: SyncState? = statuses[product.id.value]
            ProductDetailUiState.Success(product = product, syncState = state, movements = movements)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductDetailUiState.Loading,
    )
}

@dagger.assisted.AssistedFactory
interface ProductDetailViewModelFactory {
    fun create(productId: String): ProductDetailViewModel
}
