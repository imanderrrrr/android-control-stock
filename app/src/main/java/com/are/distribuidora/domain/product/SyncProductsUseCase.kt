package com.are.distribuidora.domain.product

/**
 * Caso de uso de dominio: sincronización descendente de productos (remoto -> local).
 *
 * Regla Clean:
 * - Toda decisión de flujo vive aquí.
 * - El repositorio solo hace I/O técnico.
 */
class SyncProductsUseCase(
    private val repository: ProductSyncRepository,
) {
    suspend operator fun invoke(): Unit = repository.syncProductsRemoteToLocal()
}
