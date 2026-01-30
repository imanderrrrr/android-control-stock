package com.are.distribuidora.domain.product

/**
 * Contrato de dominio para sincronizar el catálogo de productos.
 *
 * Importante:
 * - Dominio no conoce Room/Firestore.
 * - No expone Result (el caso de uso decide qué es fatal o no).
 */
interface ProductSyncRepository {
    suspend fun syncProductsRemoteToLocal()
}
