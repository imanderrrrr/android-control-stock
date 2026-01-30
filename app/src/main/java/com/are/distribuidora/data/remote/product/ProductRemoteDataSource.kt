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
interface ProductRemoteDataSource {

    data class RemoteProduct(
        val id: String,
        val name: String,
        val description: String?,
        val category: String?,
        val price: Double?,
        val imageUrl: String?,
        val barcode: String?,
        val stock: Int?,
        val comprometido: Int?,
        val createdRemoteAt: Long?,
        val updatedRemoteAt: Long?,
    )

    suspend fun fetchAllProducts(): List<RemoteProduct>
}
