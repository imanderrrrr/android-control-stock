package com.are.distribuidora.data.remote.product

/**
 * Contrato remoto (solo lectura) para catálogo de productos.
 *
 * - Implementación REAL: Firestore (ver data/remote/firestore/FirestoreProductDataSource).
 * - Implementación FAKE: FakeFirestoreProductDataSource (para tests/instrumentation).
 *
 * Importante:
 * - El Inventario NO descarga productos. Solo lee de Room.
 * - La sincronización (remoto -> local) debe consumir este contrato desde la capa data.
 */
import com.are.distribuidora.data.remote.model.RemoteProduct

interface ProductRemoteDataSource {

    /**
     * Descarga productos paginados mediante un Flow.
     * @param timestamp Timestamp de corte (updatedAt >= timestamp).
     * @param lastId ID del último documento sincronizado (para cursor compuesto explícito).
     * @param batchSize Tamaño de página (default 500).
     */
    fun fetchProductsFlow(timestamp: Long, lastId: String? = null, batchSize: Long = 500): kotlinx.coroutines.flow.Flow<List<RemoteProduct>>

    suspend fun uploadProduct(product: RemoteProduct)

    suspend fun softDeleteProduct(id: String, timestamp: Long)
}
