package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product
import javax.inject.Inject

/**
 * Caso de uso para guardar un producto (crear o actualizar).
 *
 * Reglas de Negocio:
 * 1. La persistencia es "Offline-First": se guarda localmente con SyncStatus.PENDING_UPDATE.
 * 2. El repositorio maneja la lógica de create/update automáticamente.
 * 3. Validaciones de dominio son ejecutadas por el modelo Product (init block).
 */
class SaveProductUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(product: Product) {
        repository.save(product)
    }
}

