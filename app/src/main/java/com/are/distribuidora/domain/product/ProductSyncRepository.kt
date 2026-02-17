package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.model.Product

/**
 * Contrato de dominio para sincronizar el catálogo de productos.
 *
 * Importante:
 * - Dominio no conoce Room/Firestore.
 * - No expone Result (el caso de uso decide qué es fatal o no).
 */
interface ProductSyncRepository {
    suspend fun fetchRemoteProducts(): List<Product>
    suspend fun saveLocalProducts(products: List<Product>)
    
    /**
     * Ejecuta la sincronización descendente (Remoto -> Local) usando Flow y procesamiento por lotes.
     * Evita cargar todo el dataset en memoria.
     * Maneja conflictos y persistencia transaccional por lotes.
     */
    suspend fun syncDownstream()

    suspend fun uploadPendingProducts()
}
