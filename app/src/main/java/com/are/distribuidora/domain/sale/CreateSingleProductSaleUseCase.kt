package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import javax.inject.Inject

/**
 * Caso de uso de creación/confirmación de venta para un solo producto.
 *
 * Nota: esto NO crea/persiste una entidad Sale; solo aplica la regla de stock sobre el inventario.
 */
class CreateSingleProductSaleUseCase @Inject constructor(
    private val productRepository: ProductRepository
) {

    suspend fun execute(productId: String, quantity: Int): Product {
        val pIdVO = ProductId.of(productId)
        val qtyVO = Quantity.of(quantity)

        val product = productRepository.getById(pIdVO)
            ?: throw IllegalArgumentException("Producto no encontrado: $productId")

        val updatedProduct = product.sell(qtyVO)

        productRepository.save(updatedProduct)

        return updatedProduct
    }
}
