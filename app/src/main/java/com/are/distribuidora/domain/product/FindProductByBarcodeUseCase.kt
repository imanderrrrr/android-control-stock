package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import javax.inject.Inject

/**
 * Caso de uso: busca un producto activo por su código de barras.
 * Retorna null si no existe ningún producto con ese barcode.
 */
class FindProductByBarcodeUseCase @Inject constructor(
    private val repository: ProductRepository,
) {
    suspend operator fun invoke(barcode: String): Product? {
        require(barcode.isNotBlank()) { "barcode no puede estar vacío" }
        return repository.findByBarcode(barcode)
    }
}

