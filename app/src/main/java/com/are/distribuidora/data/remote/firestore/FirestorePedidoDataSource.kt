package com.are.distribuidora.data.remote.firestore

import android.util.Log
import com.are.distribuidora.data.remote.pedido.PedidoRemoteDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Implementación Firestore de [PedidoRemoteDataSource].
 *
 * Estructura en Firestore:
 *   routes/{routeId}/orders/{orderId}                → encabezado del pedido
 *   routes/{routeId}/orders/{orderId}/items/{itemId} → ítems
 *   routes/{routeId}/order_locks/{orderKey}          → lock anti-duplicado
 *
 * Anti-duplicado server-side (segunda línea de defensa):
 * - Antes de crear el pedido se escribe un lock doc bajo order_locks/{orderKey}.
 * - Si el lock existe, está ACTIVE y expiresAt > now → DuplicateOrderRemoteException.
 * - Si el lock está expirado o INACTIVE → se sobreescribe (recovery de crashes).
 * - TTL = 15 minutos para recuperar locks huérfanos.
 *
 * Riesgo documentado sin Security Rules:
 * - Un cliente malicioso podría ignorar el lock escribiendo directamente en Firestore.
 * - La app estándar queda protegida porque SIEMPRE pasa por esta transacción.
 */
class FirestorePedidoDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : PedidoRemoteDataSource {

    private val tag = "OrdersSync"

    /** TTL del lock en milisegundos (15 minutos). */
    private val lockTtlMs = 15 * 60 * 1000L

    /**
     * Excepción interna lanzada dentro de la transacción cuando el lock ya existe activo.
     * Se captura en el catch externo y se re-lanza para que el caller la identifique.
     */
    class DuplicateOrderRemoteException(val existingOrderId: String) :
        Exception("DuplicateOrder: existingOrderId=$existingOrderId")

    override suspend fun uploadPedido(
        pedidoId: String,
        payload: PedidoRemoteDataSource.PedidoPayload,
        items: List<PedidoRemoteDataSource.PedidoItemPayload>
    ) {
        val routeId = payload.routeId
        require(routeId.isNotBlank()) { "routeId no puede estar vacío al subir pedido" }

        // vendedorId robusto: usar UID real autenticado.
        val authUid = FirebaseAuth.getInstance().currentUser?.uid
        val finalVendedorId = authUid ?: run {
            Log.w(tag, "uploadPedido: authUid null, fallback a payload.vendedorId; pedidoId=$pedidoId")
            payload.vendedorId
        }

        val orderRef = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(pedidoId)

        val orderData = mutableMapOf<String, Any?>(
            "orderId"         to pedidoId,
            "routeId"         to routeId,
            "vendedorId"      to finalVendedorId,
            "clienteId"       to payload.clienteId,
            "clientName"      to payload.clienteNombre,
            "clientAddress"   to payload.clienteDireccion,
            "sellerName"      to finalVendedorId,
            "deliveryDate"    to payload.deliveryDate,
            "subtotal"        to payload.subtotal,
            "descuentoGlobal" to payload.descuentoGlobal,
            "total"           to payload.total,
            "itemsCount"      to items.size,
            "version"         to payload.version,
            "actualizadoPor"  to payload.actualizadoPor,
            "creadoEn"        to payload.creadoEn,
            "actualizadoEn"   to payload.actualizadoEn,
        )
        payload.orderKey?.let { orderData["orderKey"] = it }

        Log.d(tag, "uploadPedido: start pedidoId=$pedidoId routeId=$routeId items=${items.size} orderKey=${payload.orderKey}")

        try {
            val itemsCollection = orderRef.collection("items")
            val now = System.currentTimeMillis()

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(orderRef)
                if (!snapshot.exists()) {
                    // ── Lock anti-duplicado ───────────────────────────────────────
                    val orderKey = payload.orderKey
                    if (orderKey != null) {
                        val lockRef = firestore
                            .collection("routes")
                            .document(routeId)
                            .collection("order_locks")
                            .document(orderKey)

                        val lockSnapshot = transaction.get(lockRef)
                        if (lockSnapshot.exists()) {
                            val status = lockSnapshot.getString("status")
                            val expiresAt = lockSnapshot.getLong("expiresAt") ?: 0L
                            val lockedOrderId = lockSnapshot.getString("orderId") ?: ""

                            if (status == "ACTIVE" && expiresAt > now) {
                                throw DuplicateOrderRemoteException(lockedOrderId)
                            }
                            Log.w(tag, "uploadPedido: lock expirado/inactivo, sobreescribiendo. orderKey=$orderKey oldOrderId=$lockedOrderId")
                        }

                        transaction.set(lockRef, mapOf(
                            "orderId"      to pedidoId,
                            "orderKey"     to orderKey,
                            "routeId"      to routeId,
                            "vendedorId"   to finalVendedorId,
                            "deliveryDate" to payload.deliveryDate,
                            "clienteId"    to payload.clienteId,
                            "createdAt"    to now,
                            "expiresAt"    to (now + lockTtlMs),
                            "status"       to "ACTIVE",
                        ))
                    }
                    // ─────────────────────────────────────────────────────────────

                    transaction.set(orderRef, orderData)
                    items.forEach { item ->
                        val itemData = mutableMapOf<String, Any?>(
                            "itemId"         to item.id,
                            "orderId"        to pedidoId,
                            "productId"      to item.productoId,
                            "productName"    to item.nombre,
                            "unitPrice"      to item.precioUnitario,
                            "quantity"       to item.cantidad,
                            "discountAmount" to item.descuentoItem,
                            "totalItem"      to item.totalItem,
                        )
                        item.notes?.let { itemData["notes"] = it }
                        transaction.set(itemsCollection.document(item.id), itemData)
                    }
                    Log.d(tag, "uploadPedido: written pedidoId=$pedidoId items=${items.size}")
                } else {
                    Log.d(tag, "uploadPedido: already exists (idempotent) pedidoId=$pedidoId")
                }
            }.await()

            Log.i(tag, "uploadPedido: done pedidoId=$pedidoId routeId=$routeId")
        } catch (e: DuplicateOrderRemoteException) {
            Log.w(tag, "uploadPedido: DuplicateOrder en Firestore pedidoId=$pedidoId existingOrderId=${e.existingOrderId}")
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(tag, "uploadPedido: Firestore error pedidoId=$pedidoId (${e.message})", e)
            throw e
        }
    }

    /**
     * Inactiva el lock cuando el pedido deja de estar activo (cancelado, completado, etc.).
     * Operación best-effort: los fallos se logean pero no se propagan.
     */
    suspend fun inactivateLock(routeId: String, orderKey: String) {
        if (routeId.isBlank() || orderKey.isBlank()) return
        try {
            firestore
                .collection("routes")
                .document(routeId)
                .collection("order_locks")
                .document(orderKey)
                .update("status", "INACTIVE")
                .await()
            Log.d(tag, "inactivateLock: done orderKey=$orderKey routeId=$routeId")
        } catch (e: Exception) {
            Log.w(tag, "inactivateLock: falló (ignorado) orderKey=$orderKey (${e.message})")
        }
    }

    override suspend fun softDeletePedido(
        routeId: String,
        pedidoId: String,
        orderKey: String?,
        deletedByUid: String?,
    ) {
        require(routeId.isNotBlank()) { "routeId no puede estar vacío al eliminar pedido" }
        require(pedidoId.isNotBlank()) { "pedidoId no puede estar vacío al eliminar pedido" }

        val orderRef = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(pedidoId)

        val now = com.google.firebase.Timestamp.now()
        val updateData = mutableMapOf<String, Any?>(
            "isDeleted"  to true,
            "deletedAt"  to now,
            "updatedAt"  to now,
        )
        if (deletedByUid != null) {
            updateData["deletedBy"] = deletedByUid
        }

        try {
            orderRef.update(updateData).await()
            Log.i(tag, "softDeletePedido: isDeleted=true pedidoId=$pedidoId routeId=$routeId uid=$deletedByUid")
        } catch (e: FirebaseFirestoreException) {
            Log.e(tag, "softDeletePedido: Firestore error pedidoId=$pedidoId (${e.message})", e)
            throw e
        }

        // Liberar lock best-effort (no abortar si falla)
        if (!orderKey.isNullOrBlank()) {
            inactivateLock(routeId = routeId, orderKey = orderKey)
        } else {
            Log.d(tag, "softDeletePedido: sin orderKey, no se libera lock pedidoId=$pedidoId")
        }
    }

    override suspend fun markPedidoExpiredInCloud(routeId: String, pedidoId: String) {
        require(routeId.isNotBlank()) { "routeId no puede estar vacío" }
        require(pedidoId.isNotBlank()) { "pedidoId no puede estar vacío" }

        val orderRef = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(pedidoId)

        val now = com.google.firebase.Timestamp.now()
        try {
            orderRef.update(
                mapOf(
                    "isDeleted"  to true,
                    "expiredAt"  to now,
                    "updatedAt"  to now,
                )
            ).await()
            Log.i(tag, "markPedidoExpiredInCloud: done pedidoId=$pedidoId routeId=$routeId")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            // Si el documento ya no existe en Firestore, lo ignoramos (idempotente)
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) {
                Log.w(tag, "markPedidoExpiredInCloud: doc not found (ignorado) pedidoId=$pedidoId")
            } else {
                Log.e(tag, "markPedidoExpiredInCloud: error pedidoId=$pedidoId (${e.message})", e)
                throw e
            }
        }
    }

    override suspend fun hardDeletePedidoFromCloud(routeId: String, pedidoId: String) {
        require(routeId.isNotBlank()) { "routeId no puede estar vacío" }
        require(pedidoId.isNotBlank()) { "pedidoId no puede estar vacío" }

        val orderRef = firestore
            .collection("routes")
            .document(routeId)
            .collection("orders")
            .document(pedidoId)

        try {
            // 1) Obtener todos los ítems para borrarlos junto con el documento raíz
            val itemsSnapshot = orderRef.collection("items").get().await()

            // 2) WriteBatch: máx 500 ops. Los pedidos normales tienen <50 ítems → seguro.
            val batch = firestore.batch()
            itemsSnapshot.documents.forEach { itemDoc ->
                batch.delete(itemDoc.reference)
            }
            batch.delete(orderRef)
            batch.commit().await()

            Log.i(tag, "hardDeletePedidoFromCloud: done pedidoId=$pedidoId routeId=$routeId items=${itemsSnapshot.size()}")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) {
                Log.w(tag, "hardDeletePedidoFromCloud: doc not found (ignorado) pedidoId=$pedidoId")
            } else {
                Log.e(tag, "hardDeletePedidoFromCloud: error pedidoId=$pedidoId (${e.message})", e)
                throw e
            }
        }
    }
}
