package com.are.distribuidora.data.remote.fake

import android.util.Log
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.remote.model.RemoteProduct

/**
 * Data source remoto FAKE para productos.
 *
 * Importante: se usa en tests/instrumentation para validar el pipeline de sync sin tocar Firestore real.
 */
class FakeFirestoreProductDataSource : ProductRemoteDataSource {

    private val products = (1..450).map { i ->
        RemoteProduct(
            id = "prod_$i",
            name = "Producto $i",
            description = "Descripcion del producto $i",
            category = "Categoria_${(i % 5) + 1}",
            price = i.toDouble(),
            imageUrl = "https://picsum.photos/seed/$i/200/200",
            barcode = "COD_$i",
            stock = i,
            comprometido = i % 7,
            isActive = true,
            isDeleted = false,
            createdRemoteAt = 1700000000L + i,
            updatedRemoteAt = 1700000000L + i,
        )
    }.toMutableList()

    override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): kotlinx.coroutines.flow.Flow<List<RemoteProduct>> = kotlinx.coroutines.flow.flow {
        val filtered = products.filter { (it.updatedRemoteAt ?: 0) >= timestamp }
            .sortedWith(compareBy({ it.updatedRemoteAt }, { it.id }))
        
        // Emulamos paginación
        var startIndex = 0
        while (startIndex < filtered.size) {
            val endIndex = minOf(startIndex + batchSize.toInt(), filtered.size)
            emit(filtered.subList(startIndex, endIndex))
            startIndex += batchSize.toInt()
        }
    }

    override suspend fun uploadProduct(product: RemoteProduct) {
        // Simple mock implementation
        val index = products.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            products[index] = product
        } else {
            products.add(product)
        }
    }

    override suspend fun softDeleteProduct(id: String, timestamp: Long) {
        val index = products.indexOfFirst { it.id == id }
        if (index >= 0) {
            val p = products[index]
            products[index] = p.copy(isDeleted = true, updatedRemoteAt = timestamp)
        }
    }
}
