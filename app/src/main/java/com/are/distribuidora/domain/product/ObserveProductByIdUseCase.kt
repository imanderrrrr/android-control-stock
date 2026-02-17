package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.ProductId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observa un producto por ID de forma reactiva.
 *
 * Offline-first: la fuente es Room (vía repository) y la UI nunca toca red.
 */
class ObserveProductByIdUseCase @Inject constructor(
    private val repo: ProductRepository,
) {
    operator fun invoke(productId: ProductId): Flow<Product?> = repo.observeById(productId)
}

