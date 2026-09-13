package com.are.distribuidora.domain.sale

import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import javax.inject.Inject

/**
 * Venta directa desde Inventario (sin pedido).
 *
 * Desde 4.1 NO escribe el contador: registra un VALE DE SALIDA (motivo OTRO, nota "Venta directa")
 * en el libro de movimientos, que ajusta el stock local y sube al servidor con increment().
 * Se permite quedar en negativo (decisión #6 del plan).
 */
class SellProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val createStockVoucher: CreateStockVoucherUseCase,
) {
    suspend fun execute(productId: String, quantity: Int): Product {
        require(quantity > 0) { "quantity debe ser mayor que 0" }
        val pIdVO = ProductId.of(productId)
        productRepository.getById(pIdVO)
            ?: throw IllegalArgumentException("Producto no encontrado: $productId")

        when (val r = createStockVoucher(
            productId = productId,
            type = MovementType.SALIDA,
            quantity = quantity,
            reason = MovementReason.OTRO,
            note = "Venta directa desde Inventario",
        )) {
            is Result.Success -> Unit
            is Result.Error -> throw IllegalStateException(
                when (val f = r.failure) {
                    is Failure.ValidationError -> f.message
                    Failure.NotFound -> "Producto no encontrado: $productId"
                    else -> "No se pudo registrar la salida"
                }
            )
        }
        return productRepository.getById(pIdVO)
            ?: throw IllegalStateException("Producto no encontrado tras la venta: $productId")
    }
}
