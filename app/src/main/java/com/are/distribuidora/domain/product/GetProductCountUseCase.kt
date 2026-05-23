package com.are.distribuidora.domain.product

import javax.inject.Inject

class GetProductCountUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    suspend operator fun invoke(): Int = repository.countAll()
}
