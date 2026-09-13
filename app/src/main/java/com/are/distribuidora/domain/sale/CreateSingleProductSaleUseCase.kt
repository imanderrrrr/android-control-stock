package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Product
import javax.inject.Inject

/**
 * Caso de uso de creación/confirmación de venta para un solo producto.
 *
 * Nota: esto NO crea/persiste una entidad Sale; solo aplica la regla de stock sobre el inventario.
 * Desde 4.1 delega en [SellProductUseCase], que registra un vale de salida en el libro de
 * movimientos en lugar de escribir el contador.
 */
class CreateSingleProductSaleUseCase @Inject constructor(
    private val sellProductUseCase: SellProductUseCase,
) {
    suspend fun execute(productId: String, quantity: Int): Product =
        sellProductUseCase.execute(productId, quantity)
}
