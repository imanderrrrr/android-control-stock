package com.are.distribuidora.presentation.productdetail

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.domain.model.Product

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState

    data class Success(
        val product: Product,
        val syncStatus: SyncStatus?,
    ) : ProductDetailUiState

    data class Error(
        val kind: ErrorKind,
    ) : ProductDetailUiState

    enum class ErrorKind {
        NOT_FOUND,
    }
}

