package com.are.distribuidora.presentation.productdetail

import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    data class Success(
        val product: Product,
        val syncState: SyncState?,
    ) : ProductDetailUiState

    data class Error(
        val kind: ErrorKind,
    ) : ProductDetailUiState

    enum class ErrorKind {
        NOT_FOUND,
    }
}

