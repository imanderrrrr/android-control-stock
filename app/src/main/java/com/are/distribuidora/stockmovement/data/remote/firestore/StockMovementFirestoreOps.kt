package com.are.distribuidora.stockmovement.data.remote.firestore

import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction

/**
 * Escritura de movimientos de inventario en Firestore, compartida por los tres caminos de
 * subida (pedidos propios, ediciones de pedidos ajenos y vales sueltos).
 *
 * Modelo remoto:
 *   stock_movements/{id} = { id, productId, productName, type, quantity, reason, orderId?, note?,
 *                            createdBy, createdByName, createdAt: serverTimestamp, clientCreatedAt }
 *   productos/{productId}.stock += ±quantity  (FieldValue.increment) y updatedAt = serverTimestamp
 *
 * Idempotencia: el SDK Android no tiene `create`, así que se emula dentro de una transacción:
 * se LEE el doc del movimiento y solo se escribe (junto con el incremento) si no existe. Un
 * reintento del worker vuelve a leer, lo encuentra y NO vuelve a incrementar. Todas las lecturas
 * de la transacción deben hacerse antes de las escrituras: por eso [readExisting] va primero.
 */
object StockMovementFirestoreOps {

    const val COLLECTION = "stock_movements"
    const val PRODUCTS_COLLECTION = "productos"

    fun movementRef(firestore: FirebaseFirestore, movementId: String): DocumentReference =
        firestore.collection(COLLECTION).document(movementId)

    fun productRef(firestore: FirebaseFirestore, productId: String): DocumentReference =
        firestore.collection(PRODUCTS_COLLECTION).document(productId)

    /** Fase de LECTURA de la transacción: qué movimientos ya existen en el servidor. */
    fun readExisting(
        transaction: Transaction,
        firestore: FirebaseFirestore,
        movements: List<StockMovement>,
    ): Map<String, DocumentSnapshot> =
        movements.associate { m -> m.id to transaction.get(movementRef(firestore, m.id)) }

    /**
     * Fase de ESCRITURA: escribe los movimientos que no existían y aplica el incremento del
     * stock por producto (agrupado, para que cada producto reciba una sola escritura).
     *
     * @return ids efectivamente escritos en esta transacción (los ya existentes se omiten).
     */
    fun writeMissing(
        transaction: Transaction,
        firestore: FirebaseFirestore,
        movements: List<StockMovement>,
        existing: Map<String, DocumentSnapshot>,
    ): List<String> {
        val toWrite = movements.filter { existing[it.id]?.exists() != true }
        if (toWrite.isEmpty()) return emptyList()

        toWrite.forEach { m ->
            transaction.set(movementRef(firestore, m.id), toRemoteMap(m))
        }
        toWrite.groupBy { it.productId }
            .mapValues { (_, ms) -> ms.sumOf { it.signedQuantity }.toLong() }
            .forEach { (productId, delta) ->
                // set + merge: si el producto aún no existe remotamente (creado offline), el
                // incremento crea el doc con solo stock/updatedAt y el sync de productos lo
                // completa después con merge. Con update() fallaría toda la transacción.
                transaction.set(
                    productRef(firestore, productId),
                    mapOf(
                        "stock" to FieldValue.increment(delta),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
            }
        return toWrite.map { it.id }
    }

    fun toRemoteMap(m: StockMovement): Map<String, Any?> = buildMap {
        put("id", m.id)
        put("productId", m.productId)
        put("productName", m.productName)
        put("type", m.type.name)
        put("quantity", m.quantity)
        put("reason", m.reason.name)
        m.orderId?.let { put("orderId", it) }
        m.note?.let { put("note", it) }
        put("createdBy", m.createdBy)
        put("createdByName", m.createdByName)
        put("createdAt", FieldValue.serverTimestamp())
        put("clientCreatedAt", m.createdAt)
    }
}
