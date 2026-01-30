package com.are.distribuidora.data.remote.sale

/**
 * Contrato remoto para subir ventas.
 * Implementaciones concretas (Firestore, fakes) deben cumplir esta interfaz.
 */
interface SaleRemoteDataSource {
    data class ItemPayload(
        val productId: String,
        val productName: String,
        val unitPrice: Double,
        val quantity: Int,
    )

    data class ClientPayload(
        val name: String,
        val clientId: String? = null,
        val address: String? = null,
    )

    data class SalePayload(
        val id: String,
        val sellerId: String,
        val createdBy: String,
        val finalizedBy: String?,
        val client: ClientPayload,
        val items: List<ItemPayload>,
    )

    suspend fun uploadSale(
        saleId: String,
        sellerId: String,
        createdBy: String,
        finalizedBy: String?,
        items: List<ItemPayload>
    )

    /**
     * Obtiene ventas desde el remoto (Firestore). Firestore es la fuente de verdad.
     */
    suspend fun fetchSales(): List<SalePayload>
}
