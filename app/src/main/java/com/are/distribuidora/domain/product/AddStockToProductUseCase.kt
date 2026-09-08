package com.are.distribuidora.domain.product

import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase

/**
 * Caso de uso: "Agregar stock" (escáner de Inventario) = VALE DE ENTRADA.
 *
 * Desde 4.1 ya no suma al contador: registra un movimiento ENTRADA en el libro (motivo por
 * defecto COMPRA) que ajusta el stock local y sube al servidor con un incremento atómico.
 *
 * Reglas:
 * - delta > 0 (no se permite reducir stock con este use case; para eso hay vales de salida).
 * - Si el producto no existe en la base local, lanza NoSuchElementException.
 */
class AddStockToProductUseCase(
    private val createStockVoucher: CreateStockVoucherUseCase,
) {
    /**
     * @param productId ID del producto al que se suma stock.
     * @param delta Cantidad de unidades a sumar. Debe ser > 0.
     * @param reason Motivo manual (COMPRA por defecto). Nunca uno automático.
     * @throws IllegalArgumentException si delta <= 0 o el motivo no es válido.
     * @throws NoSuchElementException si el producto no existe.
     */
    suspend operator fun invoke(
        productId: String,
        delta: Int,
        reason: MovementReason = MovementReason.COMPRA,
        note: String? = null,
    ): StockMovement {
        require(delta > 0) { "La cantidad a agregar debe ser mayor a 0, pero fue $delta" }
        return when (val r = createStockVoucher(productId, MovementType.ENTRADA, delta, reason, note)) {
            is Result.Success -> r.value
            is Result.Error -> when (val f = r.failure) {
                Failure.NotFound -> throw NoSuchElementException("Producto no encontrado: $productId")
                is Failure.ValidationError -> throw IllegalArgumentException(f.message)
                else -> throw IllegalStateException("No se pudo registrar el vale de entrada")
            }
        }
    }
}
