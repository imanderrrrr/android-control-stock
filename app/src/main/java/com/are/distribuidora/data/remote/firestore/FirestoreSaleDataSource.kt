package com.are.distribuidora.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.are.distribuidora.data.remote.sale.SaleRemoteDataSource
import kotlinx.coroutines.tasks.await

/**
 * Data source remoto para ventas en Firestore.
 * - No conoce Room ni SyncStatus.
 * - Sólo envía datos de negocio.
 */
class FirestoreSaleDataSource(
    private val firestore: FirebaseFirestore
) : SaleRemoteDataSource {
    private val collection = firestore.collection("sales")

    /**
     * Sube una venta al documento con id [saleId].
     * Envía exclusivamente campos de negocio.
     */
    override suspend fun uploadSale(
        saleId: String,
        sellerId: String,
        createdBy: String,
        finalizedBy: String?,
        items: List<SaleRemoteDataSource.ItemPayload>
    ) {
        val payload = hashMapOf(
            "sellerId" to sellerId,
            "createdBy" to createdBy,
            "finalizedBy" to finalizedBy,
            // Compatibilidad: este datasource aún no recibe client en uploadSale();
            // el flujo híbrido actual no sube datos de cliente.
            "items" to items.map { it.toMap() }
        )
        collection.document(saleId).set(payload).await()
    }

    /**
     * Consulta todas las ventas.
     */
    override suspend fun fetchSales(): List<SaleRemoteDataSource.SalePayload> {
        val snapshot = collection.get().await()

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null

            val sellerId = data["sellerId"] as? String ?: return@mapNotNull null
            val createdBy = data["createdBy"] as? String ?: return@mapNotNull null
            val finalizedBy = data["finalizedBy"] as? String

            // Cliente embebido (nuevo diseño). Lectura defensiva para datos legacy.
            @Suppress("UNCHECKED_CAST")
            val clientMap = data["client"] as? Map<String, Any?>
            val clientName = clientMap?.get("name") as? String
            val clientId = clientMap?.get("clientId") as? String
            val address = clientMap?.get("address") as? String

            val clientPayload = SaleRemoteDataSource.ClientPayload(
                name = clientName ?: "Cliente",
                clientId = clientId,
                address = address,
            )

            @Suppress("UNCHECKED_CAST")
            val rawItems = data["items"] as? List<Map<String, Any?>> ?: emptyList()

            val items = rawItems.mapNotNull { itemMap ->
                val productIdAny = itemMap["productId"] ?: return@mapNotNull null
                val productId = productIdAny.toString()
                val productName = (itemMap["productName"] as? String) ?: return@mapNotNull null

                val unitPriceAny = itemMap["unitPrice"] ?: return@mapNotNull null
                val unitPrice = when (unitPriceAny) {
                    is Number -> unitPriceAny.toDouble()
                    is String -> unitPriceAny.toDoubleOrNull() ?: return@mapNotNull null
                    else -> return@mapNotNull null
                }

                val quantityAny = itemMap["quantity"] ?: return@mapNotNull null
                val quantity = when (quantityAny) {
                    is Number -> quantityAny.toInt()
                    is String -> quantityAny.toIntOrNull() ?: return@mapNotNull null
                    else -> return@mapNotNull null
                }

                SaleRemoteDataSource.ItemPayload(
                    productId = productId,
                    productName = productName,
                    unitPrice = unitPrice,
                    quantity = quantity,
                )
            }

            SaleRemoteDataSource.SalePayload(
                id = doc.id,
                sellerId = sellerId,
                createdBy = createdBy,
                finalizedBy = finalizedBy,
                client = clientPayload,
                items = items,
            )
        }
    }

    private fun SaleRemoteDataSource.ItemPayload.toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to productName,
        "unitPrice" to unitPrice,
        "quantity" to quantity,
    )
}
