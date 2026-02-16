package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso de dominio: observación reactiva del catálogo de productos.
 *
 * - No toca Room/Firestore.
 * - Solo delega al contrato [ProductRepository].
 */
import androidx.paging.PagingData

class ObserveProductsUseCase(
    private val repo: ProductRepository,
) {
    operator fun invoke(query: String? = null): Flow<PagingData<Product>> = repo.getProductsStream(query)
}
