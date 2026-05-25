package com.are.distribuidora.data.remote.firestore

import android.util.Log
import com.are.distribuidora.core.images.FirestoreImageUrlValidator
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.remote.model.RemoteProduct
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.Date
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Implementación REAL (producción) de descarga de productos desde Firestore.
 *
 * - Colección: `productos`
 * - Descarga TODOS los documentos (sin filtros, sin paginación).
 * - NO cachea aquí.
 * - NO toca Room aquí.
 *
 * Importante:
 * - El Inventario NO descarga productos. Solo lee de Room.
 * - Un repositorio de sync (remoto -> local) consumirá este data source y persistirá en Room.
 * - Existe un FakeFirestoreProductDataSource para tests. Ese fake NO debe eliminarse.
 */
class FirestoreProductDataSource(
    private val firestore: FirebaseFirestore,
) : ProductRemoteDataSource {

    private val tag = "SYNC_PRODUCT"
    private val collectionName = "productos"

    private fun anyToEpochMillis(value: Any?): Long? {
        return when (value) {
            null -> null
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
            is Date -> value.time
            is Timestamp -> value.toDate().time
            else -> null
        }
    }

    override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): kotlinx.coroutines.flow.Flow<List<RemoteProduct>> = flow {
        Log.d(tag, "FirestoreProductDataSource.fetchProductsFlow: Querying products modified after $timestamp (lastId=$lastId) with batchSize=$batchSize")
        
        val queryTimestamp = if (timestamp > 0) Date(timestamp) else Date(0)
        var lastVisible: com.google.firebase.firestore.DocumentSnapshot? = null
        
        // Initial Cursor Setup for Resumption
        if (lastId != null && timestamp > 0) {
            try {
                // Enterprise-Grade: Explicitly fetch the exact cursor document to avoid ambiguity
                val cursorDoc = firestore.collection(collectionName).document(lastId).get().await()
                if (cursorDoc.exists()) {
                    lastVisible = cursorDoc
                    Log.d(tag, "Resuming from specific cursor document: $lastId")
                } else {
                    Log.w(tag, "Cursor document $lastId not found. Falling back to timestamp only.")
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to fetch cursor document $lastId. Falling back to timestamp only.", e)
            }
        }
        
        while (true) {
            // Composite Index Required: updatedAt ASC, __name__ ASC
            var query = firestore.collection(collectionName)
                .whereGreaterThanOrEqualTo("updatedAt", queryTimestamp)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), com.google.firebase.firestore.Query.Direction.ASCENDING)
                .limit(batchSize)

            if (lastVisible != null) {
                // Stable pagination using cursor
                query = query.startAfter(lastVisible!!)
            }

            val snapshot = try {
                 query.get().await()
            } catch (e: Exception) {
                Log.e(tag, "Error fetching batch from Firestore", e)
                throw e // Let repository handle retry/error
            }

            if (snapshot.isEmpty) {
                Log.d(tag, "Fetch flow completed: No more documents.")
                break
            }

            val products = snapshot.documents.mapNotNull { doc ->
                mapDocumentToRemoteProduct(doc)
            }
            
            if (products.isNotEmpty()) {
                emit(products)
            }

            if (snapshot.size() < batchSize) {
                Log.d(tag, "Fetch flow completed: Last batch was smaller than limit.")
                break
            }
            
            lastVisible = snapshot.documents.last()
        }
    }.flowOn(Dispatchers.IO)

    private fun mapDocumentToRemoteProduct(doc: com.google.firebase.firestore.DocumentSnapshot): RemoteProduct? {
        val id = doc.id
        if (id.isBlank()) return null

        val name = doc.getString("name") ?: doc.getString("nombre")
        if (name.isNullOrBlank()) return null

        val price = doc.getDouble("price") ?: doc.getDouble("precio")
        val description = doc.getString("description") ?: doc.getString("descripcion")
        val category = doc.getString("category") ?: doc.getString("categoria")
        val imageUrl = doc.getString("imagenUrl") ?: doc.getString("imageUrl")
        val barcode = doc.getString("codigoBarras") ?: doc.getString("barcode")
        val stock = (doc.getLong("stock") ?: doc.getLong("existencias"))?.toInt()
        val comprometido = (doc.getLong("comprometido"))?.toInt()
        val isActive = doc.getBoolean("isActive")
        val isDeleted = doc.getBoolean("isDeleted")

        val createdRemoteAt = anyToEpochMillis(doc.get("creadoEn"))
            ?: anyToEpochMillis(doc.get("createdRemoteAt"))
            ?: anyToEpochMillis(doc.get("createdAt"))

        val updatedRemoteAt = anyToEpochMillis(doc.get("actualizadoEn"))
            ?: anyToEpochMillis(doc.get("updatedRemoteAt"))
            ?: anyToEpochMillis(doc.get("updatedAt"))

        return RemoteProduct(
            id = id,
            name = name,
            description = description,
            category = category,
            price = price,
            imageUrl = imageUrl,
            barcode = barcode,
            stock = stock,
            comprometido = comprometido,
            isActive = isActive,
            isDeleted = isDeleted,
            createdRemoteAt = createdRemoteAt,
            updatedRemoteAt = updatedRemoteAt,
        )
    }

    override suspend fun uploadProduct(product: RemoteProduct) {
        try {
            Log.d(tag, "FirestoreProductDataSource.uploadProduct: Uploading ${product.id}")

            // VALIDACIÓN G: NUNCA escribir rutas locales en Firestore
            FirestoreImageUrlValidator.validateForFirestore(product.imageUrl)

            val data = hashMapOf<String, Any?>(
                "name" to product.name,
                "description" to product.description,
                "category" to product.category,
                "price" to product.price,
                "imageUrl" to product.imageUrl,
                "barcode" to product.barcode,
                "stock" to product.stock,
                "isActive" to product.isActive,
                "isDeleted" to product.isDeleted,
                "createdAt" to product.createdRemoteAt?.let { Date(it) },
                "updatedAt" to FieldValue.serverTimestamp() // SERVER AUTHORITY: Always use server timestamp
            )

            // FIX RACE CONDITION: filtrar campos null para no sobrescribir datos
            // existentes en Firestore. Crítico para imageUrl: si la imagen aún no se
            // subió (imageUrl=null), NO queremos borrar un imageUrl que ya exista
            // remotamente (puesto por UploadPendingProductImagesWorker).
            // Usamos SetOptions.merge() para que solo se actualicen los campos presentes.
            val filteredData = data.filterValues { it != null }

            Log.d(tag, "FirestoreProductDataSource.uploadProduct: fields=${filteredData.keys}, hasImageUrl=${filteredData.containsKey("imageUrl")}")

            firestore.collection(collectionName).document(product.id)
                .set(filteredData, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d(tag, "FirestoreProductDataSource.uploadProduct: Successfully uploaded ${product.id}")
        } catch (e: Exception) {
            Log.e(tag, "FirestoreProductDataSource.uploadProduct: Error uploading product ${product.id}", e)
            throw e
        }
    }

    override suspend fun softDeleteProduct(id: String, timestamp: Long) {
        try {
            Log.d(tag, "FirestoreProductDataSource.softDeleteProduct: Soft deleting $id with server timestamp")
            val updateData = mapOf(
                "isDeleted" to true,
                "updatedAt" to FieldValue.serverTimestamp() // SERVER AUTHORITY: Always use server timestamp
            )
            firestore.collection(collectionName).document(id).update(updateData).await()
            Log.d(tag, "FirestoreProductDataSource.softDeleteProduct: Successfully soft deleted $id")
        } catch (e: Exception) {
            Log.e(tag, "FirestoreProductDataSource.softDeleteProduct: Error soft deleting product $id", e)
            throw e
        }
    }
}
