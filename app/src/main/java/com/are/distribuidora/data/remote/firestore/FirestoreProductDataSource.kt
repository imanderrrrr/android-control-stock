package com.are.distribuidora.data.remote.firestore

import android.util.Log
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.tasks.await

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

    private val tag = "ProductSync"
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

    override suspend fun fetchAllProducts(): List<ProductRemoteDataSource.RemoteProduct> {
        Log.d(tag, "Fetching products from Firestore collection '$collectionName'")

        return try {
            Log.d(tag, "About to query Firestore: collection='$collectionName'")
            val snap = firestore.collection(collectionName).get().await()

            val docs = snap.documents
            Log.d(tag, "Firestore returned ${docs.size} products")
            if (docs.isEmpty()) {
                Log.w(tag, "Firestore collection '$collectionName' returned 0 documents")
            }

            val mapped = docs.mapNotNull { doc ->
                val id = doc.id
                if (id.isBlank()) {
                    Log.w(tag, "Skipping product with blank Firestore doc.id")
                    return@mapNotNull null
                }

                // Mapeo defensivo (sin lógica de negocio: solo null-checks y tipos)
                val name = doc.getString("name") ?: doc.getString("nombre")
                if (name.isNullOrBlank()) {
                    Log.w(tag, "Skipping product id=$id because name is null/blank")
                    return@mapNotNull null
                }

                val price = doc.getDouble("price") ?: doc.getDouble("precio")
                val description = doc.getString("description") ?: doc.getString("descripcion")
                val category = doc.getString("category") ?: doc.getString("categoria")

                // IMPORTANTE (FIX): en Firestore el campo real es `imagenUrl` (español).
                // `imageUrl` se mantiene como fallback por compatibilidad, pero NO se transforma el URL.
                val imageUrl = doc.getString("imagenUrl") ?: doc.getString("imageUrl")

                // Blindaje mínimo solicitado: no reemplazar por "". Solo loggear.
                if (imageUrl.isNullOrBlank()) {
                    Log.w(tag, "Product $id has null imagenUrl")
                }

                val barcode = doc.getString("codigoBarras") ?: doc.getString("barcode")

                val stock = (doc.getLong("stock") ?: doc.getLong("existencias"))?.toInt()
                val comprometido = (doc.getLong("comprometido"))?.toInt()

                val createdRemoteAt = anyToEpochMillis(doc.get("creadoEn"))
                    ?: anyToEpochMillis(doc.get("createdRemoteAt"))
                    ?: anyToEpochMillis(doc.get("createdAt"))

                val updatedRemoteAt = anyToEpochMillis(doc.get("actualizadoEn"))
                    ?: anyToEpochMillis(doc.get("updatedRemoteAt"))
                    ?: anyToEpochMillis(doc.get("updatedAt"))

                ProductRemoteDataSource.RemoteProduct(
                    id = id,
                    name = name,
                    description = description,
                    category = category,
                    price = price,
                    imageUrl = imageUrl,
                    barcode = barcode,
                    stock = stock,
                    comprometido = comprometido,
                    createdRemoteAt = createdRemoteAt,
                    updatedRemoteAt = updatedRemoteAt,
                )
            }

            Log.d(tag, "Mapped documents -> entities count=${mapped.size}")
            mapped
        } catch (e: Exception) {
            Log.e(tag, "Error fetching products from Firestore collection '$collectionName'", e)
            // Blindaje mínimo: ante error devolvemos lista vacía (sin cambiar reglas de negocio),
            // y el repositorio de sync decidirá cómo reportar el fallo.
            emptyList()
        }
    }
}
