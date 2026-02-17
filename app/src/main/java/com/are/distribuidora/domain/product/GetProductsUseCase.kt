package com.are.distribuidora.domain.product

import androidx.paging.PagingData
import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

class GetProductsUseCase(
    private val repo: ProductRepository,
) {
    operator fun invoke(): Flow<PagingData<Product>> = repo.getProductsStream(null)
}
