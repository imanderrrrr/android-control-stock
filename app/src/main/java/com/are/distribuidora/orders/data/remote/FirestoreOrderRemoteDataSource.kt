package com.are.distribuidora.orders.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firestore (solo lectura) para pedidos.
 *
 * Estructura remota esperada:
 * routes/{routeId}/orders/{orderId}
 *  - Campos mínimos: orderId, routeId, deliveryDate, clientName, clientAddress, sellerName, itemsCount
 *  - items: array con items completos
 */
class FirestoreOrderRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : OrderRemoteDataSource {

    private val tag = "OrdersSync"

    override suspend fun fetchOrderHeaders(routeId: String, deliveryDate: String): List<OrderRemoteDataSource.OrderHeaderDto> {
        val snapshot = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .whereEqualTo("deliveryDate", deliveryDate)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null

            val orderId = ((data["orderId"] as? String) ?: doc.id).trim()
            val rId = ((data["routeId"] as? String) ?: routeId).trim()
            val date = ((data["deliveryDate"] as? String) ?: "").trim()
            val clientName = ((data["clientName"] as? String) ?: "").trim()
            val clientAddress = data["clientAddress"] as? String
            val sellerName = data["sellerName"] as? String

            if (orderId.isBlank() || rId.isBlank() || date.isBlank() || clientName.isBlank()) {
                Log.w(tag, "fetchOrderHeaders: header incompleto; skip docId=${doc.id}")
                return@mapNotNull null
            }

            val itemsCountAny = data["itemsCount"]
            val parsedCount = when (itemsCountAny) {
                is Number -> itemsCountAny.toInt()
                is String -> itemsCountAny.toIntOrNull()
                else -> null
            } ?: 0
            val itemsCount = if (parsedCount < 0) 0 else parsedCount

            OrderRemoteDataSource.OrderHeaderDto(
                orderId = orderId,
                routeId = rId,
                deliveryDate = date,
                clientName = clientName,
                clientAddress = clientAddress,
                sellerName = sellerName,
                itemsCount = itemsCount,
            )
        }
    }

    override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
        val doc = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(orderId)
            .get()
            .await()

        val data = doc.data ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val rawItems = data["items"] as? List<Map<String, Any?>> ?: emptyList()

        val items = rawItems.mapNotNull { itemMap ->
            val productIdAny = itemMap["productId"] ?: return@mapNotNull null
            val productId = productIdAny.toString().trim()
            if (productId.isBlank()) return@mapNotNull null

            val productName = (itemMap["productName"] as? String)?.trim().orEmpty()
            if (productName.isBlank()) return@mapNotNull null

            val unitPriceAny = itemMap["unitPrice"] ?: return@mapNotNull null
            val unitPrice = when (unitPriceAny) {
                is Number -> unitPriceAny.toDouble()
                is String -> unitPriceAny.toDoubleOrNull() ?: return@mapNotNull null
                else -> return@mapNotNull null
            }
            if (unitPrice < 0.0) return@mapNotNull null

            val quantityAny = itemMap["quantity"] ?: return@mapNotNull null
            val quantity = when (quantityAny) {
                is Number -> quantityAny.toInt()
                is String -> quantityAny.toIntOrNull() ?: return@mapNotNull null
                else -> return@mapNotNull null
            }
            if (quantity <= 0) return@mapNotNull null

            OrderRemoteDataSource.OrderItemDto(
                productId = productId,
                productName = productName,
                unitPrice = unitPrice,
                quantity = quantity,
            )
        }

        if (items.isEmpty() && rawItems.isNotEmpty()) {
            Log.w(tag, "fetchOrderItems: items inválidos/filtrados; orderId=$orderId raw=${rawItems.size}")
        }

        return items
    }
}
