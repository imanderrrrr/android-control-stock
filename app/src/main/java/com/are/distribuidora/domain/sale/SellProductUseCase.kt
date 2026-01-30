package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import javax.inject.Inject

/**
 * Venta atómica de inventario:
 * 1) aplica regla de dominio vía [com.are.distribuidora.domain.model.Product.sell]
 * 2) persiste el stock resultante
 *
 * Solo retorna éxito si ambos pasos ocurren.
 */
class SellProductUseCase @Inject constructor(
    private val productRepository: com.are.distribuidora.domain.product.ProductRepository,
) {
    suspend fun execute(productId: String, quantity: Int): com.are.distribuidora.domain.model.Product {
        val qtyVO = Quantity.of(quantity)
        val pIdVO = ProductId.of(productId)
        
        val current = productRepository.getById(pIdVO)
            ?: throw IllegalArgumentException("Producto no encontrado: $productId")
            
        val updated = current.sell(qtyVO)
        productRepository.save(updated) // Transactional integrity depends on repo implementation
        return updated
    }
}
