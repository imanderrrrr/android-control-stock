package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso de dominio: observación reactiva del catálogo de productos.
 *
 * - No toca Room/Firestore.
 * - Solo delega al contrato [ProductRepository].
 */
class ObserveProductsUseCase(
    private val repo: ProductRepository,
) {
    operator fun invoke(): Flow<List<Product>> = repo.getProducts()
}
