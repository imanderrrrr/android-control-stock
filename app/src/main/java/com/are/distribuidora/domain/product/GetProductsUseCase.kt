package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

class GetProductsUseCase(
    private val repo: ProductRepository,
) {
    operator fun invoke(): Flow<List<Product>> = repo.getProducts()
}
