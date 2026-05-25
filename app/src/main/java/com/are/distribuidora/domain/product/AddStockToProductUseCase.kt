package com.are.distribuidora.domain.product

import javax.inject.Inject

/**
 * Caso de uso: suma [delta] unidades al stock de un producto identificado por [productId].
 *
 * Reglas de negocio:
 * - delta debe ser > 0 (no se permite reducir stock con este use case).
 * - La actualización es transaccional (Room @Transaction en el repositorio).
 * - Si el producto no existe en la base local, lanza IllegalArgumentException.
 * - Marca el producto como PENDING_UPDATE para que el sync lo propague.
 */
class AddStockToProductUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    /**
     * @param productId ID del producto al que se suma stock.
     * @param delta Cantidad de unidades a sumar. Debe ser > 0.
     * @throws IllegalArgumentException si delta <= 0.
     * @throws NoSuchElementException si el producto no existe.
     */
    suspend operator fun invoke(productId: String, delta: Int) {
        require(delta > 0) { "La cantidad a agregar debe ser mayor a 0, pero fue $delta" }
        val exists = repository.getById(
            com.are.distribuidora.domain.valueobject.ProductId.of(productId)
        ) ?: throw NoSuchElementException("Producto no encontrado: $productId")
        repository.incrementStock(productId, delta)
    }
}

