package com.are.distribuidora.domain.product

import javax.inject.Inject

class DeleteProductUseCase @Inject constructor(
    private val productRepository: ProductRepository
) {
    suspend operator fun invoke(id: String) {
        productRepository.delete(id)
    }
}
