package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.valueobject.ProductId
import javax.inject.Inject

/**
 * Obtiene la URL/URI de imagen de un producto por su ID.
 *
 * Prioridad: imageUrl remota (https) → imageLocalUri (local) → null.
 *
 * Responsabilidad única: resolver qué fuente de imagen mostrar para un productoId.
 * Úsalo desde la capa de presentación para enriquecer modelos de UI.
 */
class GetProductImageUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {
    /**
     * @return URL/URI lista para cargar con Glide, o null si no hay imagen.
     */
    suspend operator fun invoke(productoId: String): String? {
        val product = productRepository.getById(ProductId.of(productoId)) ?: return null
        return product.imageUrl?.takeIf { it.isNotBlank() }
            ?: product.imageLocalUri?.takeIf { it.isNotBlank() }
    }
}


