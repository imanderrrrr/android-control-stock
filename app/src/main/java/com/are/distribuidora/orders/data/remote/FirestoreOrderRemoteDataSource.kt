package com.are.distribuidora.orders.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firestore (lectura) para pedidos.
 *
 * Estructura remota (única fuente de verdad para pedidos):
 * routes/{routeId}/orders/{orderId}
 *  - Campos mínimos: orderId, routeId, deliveryDate, clientName, clientAddress, sellerName, itemsCount
 *
 * routes/{routeId}/orders/{orderId}/items/{itemId}
 *  - Campos: itemId, productId, productName, unitPrice, quantity
 *
 * NOTA: La colección legacy "pedidos" ya NO se consulta en este flujo.
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
            // vendedorId: UID del creador. Puede ser null en pedidos legacy.
            val vendedorId = (data["vendedorId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            // isDeleted: soft delete. Default false para compatibilidad con docs legacy sin el campo.
            val isDeleted = (data["isDeleted"] as? Boolean) ?: false

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
                vendedorId = vendedorId,
                isDeleted = isDeleted,
            )
        }
    }

    override suspend fun fetchAllOrderHeaders(routeId: String): List<OrderRemoteDataSource.OrderHeaderDto> {
        val snapshot = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
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
            val vendedorId = (data["vendedorId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val isDeleted = (data["isDeleted"] as? Boolean) ?: false

            if (orderId.isBlank() || rId.isBlank() || clientName.isBlank()) {
                Log.w(tag, "fetchAllOrderHeaders: header incompleto; skip docId=${doc.id}")
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
                vendedorId = vendedorId,
                isDeleted = isDeleted,
            )
        }
    }

    /**
     * Lee la subcolección routes/{routeId}/orders/{orderId}/items.
     * Cada doc de la subcolección representa un item con su itemId como docId.
     */
    override suspend fun fetchOrderItems(routeId: String, orderId: String): List<OrderRemoteDataSource.OrderItemDto> {
        Log.d(tag, "fetchOrderItems: start orderId=$orderId routeId=$routeId")

        val snapshot = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(orderId)
            .collection("items")
            .get()
            .await()

        if (snapshot.isEmpty) {
            Log.w(tag, "fetchOrderItems: subcolección vacía orderId=$orderId")
            return emptyList()
        }

        val items = snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null

            // itemId: preferir campo explícito, si no usar docId
            val itemId = ((data["itemId"] as? String)?.trim()?.takeIf { it.isNotBlank() }) ?: doc.id

            val productId = (data["productId"] as? String)?.trim()
                ?: return@mapNotNull null.also {
                    Log.w(tag, "fetchOrderItems: item sin productId; skip docId=${doc.id}")
                }
            if (productId.isBlank()) return@mapNotNull null

            val productName = (data["productName"] as? String)?.trim().orEmpty()
            if (productName.isBlank()) return@mapNotNull null

            val unitPriceAny = data["unitPrice"] ?: return@mapNotNull null
            val unitPrice = when (unitPriceAny) {
                is Number -> unitPriceAny.toDouble()
                is String -> unitPriceAny.toDoubleOrNull() ?: return@mapNotNull null
                else -> return@mapNotNull null
            }
            if (unitPrice < 0.0) return@mapNotNull null

            val quantityAny = data["quantity"] ?: return@mapNotNull null
            val quantity = when (quantityAny) {
                is Number -> quantityAny.toInt()
                is String -> quantityAny.toIntOrNull() ?: return@mapNotNull null
                else -> return@mapNotNull null
            }
            if (quantity <= 0) return@mapNotNull null

            val notes = (data["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

            OrderRemoteDataSource.OrderItemDto(
                itemId = itemId,
                productId = productId,
                productName = productName,
                unitPrice = unitPrice,
                quantity = quantity,
                notes = notes,
            )
        }

        Log.d(tag, "fetchOrderItems: end orderId=$orderId fetched=${items.size} rawDocs=${snapshot.size()}")

        if (items.isEmpty() && snapshot.documents.isNotEmpty()) {
            Log.w(tag, "fetchOrderItems: todos los items fueron filtrados por datos inválidos; orderId=$orderId raw=${snapshot.size()}")
        }

        return items
    }

    /**
     * Soft delete en Firestore: actualiza isDeleted=true en el doc del pedido.
     * NO borra físicamente el documento ni sus items.
     *
     * Campos actualizados:
     *   - isDeleted = true
     *   - updatedAt = Timestamp.now()
     *
     * Patrón idéntico al de productos/clientes (solo isDeleted + updatedAt, sin deletedAt/deletedBy
     * porque el modelo base no los usa).
     */
    override suspend fun markOrderDeleted(routeId: String, orderId: String, deletedByUid: String?) {
        if (routeId.isBlank() || orderId.isBlank()) {
            Log.w(tag, "markOrderDeleted: routeId o orderId vacío; skip")
            return
        }

        val updates: Map<String, Any> = buildMap {
            put("isDeleted", true)
            put("updatedAt", Timestamp.now())
            // deletedBy no es parte del patrón base (productos/clientes no lo usan)
            // pero se loguea para auditoría
        }

        firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(orderId)
            .update(updates)
            .await()

        Log.i(tag, "markOrderDeleted: ok orderId=$orderId routeId=$routeId deletedBy=$deletedByUid")
    }

    /**
     * Sube una edición de ítems con un WriteBatch atómico:
     *  - delete de los docs de items que ya no existen en [items],
     *  - set (crea/sobrescribe) de los items presentes (cada itemId es el docId),
     *  - update del header SOLO con itemsCount/totalAmount/updatedAt/lastModifiedBy.
     *
     * Importante (preservación del dueño): se usa .update() con un set acotado de campos;
     * NUNCA se escribe vendedorId ni sellerName, así el pedido sigue siendo del vendedor
     * original. Cada documento recibe a lo sumo una escritura en el batch (un id que se
     * conserva va por `set`, nunca por `delete`), evitando el error de Firestore de
     * "multiple writes per document".
     */
    override suspend fun uploadOrderEdit(
        routeId: String,
        orderId: String,
        items: List<OrderRemoteDataSource.OrderItemDto>,
        totalAmount: Double,
        editedByUid: String?,
    ) {
        if (routeId.isBlank() || orderId.isBlank()) {
            Log.w(tag, "uploadOrderEdit: routeId u orderId vacío; skip")
            return
        }
        if (items.isEmpty()) {
            // Un pedido editado nunca queda sin ítems (la validación de dominio lo impide).
            Log.w(tag, "uploadOrderEdit: items vacío orderId=$orderId; skip para no corromper remoto")
            return
        }

        val orderRef = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(orderId)
        val itemsRef = orderRef.collection("items")

        // Leer los docs actuales para saber cuáles borrar (los que ya no están en la edición).
        val existing = itemsRef.get().await()
        val newIds = items.map { it.itemId }.toHashSet()

        val batch = firestore.batch()

        // 1) Borrar ítems quitados en la edición.
        var deleted = 0
        existing.documents.forEach { doc ->
            if (doc.id !in newIds) {
                batch.delete(doc.reference)
                deleted++
            }
        }

        // 2) Crear/actualizar los ítems vigentes (set por itemId = docId).
        items.forEach { item ->
            val data = hashMapOf(
                "itemId" to item.itemId,
                "productId" to item.productId,
                "productName" to item.productName,
                "unitPrice" to item.unitPrice,
                "quantity" to item.quantity,
                "notes" to item.notes,
            )
            batch.set(itemsRef.document(item.itemId), data)
        }

        // 3) Header: SOLO campos mutables. NO vendedorId/sellerName (preserva al dueño).
        val headerUpdates: Map<String, Any> = buildMap {
            put("itemsCount", items.size)
            put("totalAmount", totalAmount)
            put("updatedAt", Timestamp.now())
            put("lastModifiedBy", editedByUid ?: "")
        }
        batch.update(orderRef, headerUpdates)

        batch.commit().await()

        Log.i(
            tag,
            "uploadOrderEdit: ok orderId=$orderId routeId=$routeId set=${items.size} deleted=$deleted total=$totalAmount editedBy=$editedByUid",
        )
    }
}
