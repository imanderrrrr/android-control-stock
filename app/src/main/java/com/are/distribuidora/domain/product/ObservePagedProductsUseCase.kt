package com.are.distribuidora.domain.product

import androidx.paging.PagingData
import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePagedProductsUseCase @Inject constructor(
    private val repo: ProductRepository,
) {
    operator fun invoke(query: String? = null): Flow<PagingData<Product>> = repo.getProductsStream(query)
}
