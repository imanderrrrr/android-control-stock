package com.are.distribuidora.data.remote.fake

import android.util.Log
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource

/**
 * Data source remoto FAKE para productos.
 *
 * Importante: se usa en tests/instrumentation para validar el pipeline de sync sin tocar Firestore real.
 */
class FakeFirestoreProductDataSource : ProductRemoteDataSource {

    override suspend fun fetchAllProducts(): List<ProductRemoteDataSource.RemoteProduct> {
        Log.d("ProductSync", "FakeFirestoreProductDataSource.fetchAllProducts() called")

        val products = (1..450).map { i ->
            ProductRemoteDataSource.RemoteProduct(
                id = "prod_$i",
                name = "Producto $i",
                description = "Descripcion del producto $i",
                category = "Categoria_${(i % 5) + 1}",
                price = i.toDouble(),
                imageUrl = "https://picsum.photos/seed/$i/200/200",
                barcode = "COD_$i",
                stock = i,
                comprometido = i % 7,
                createdRemoteAt = 1700000000L + i,
                updatedRemoteAt = 1700000000L + i,
            )
        }

        Log.d("ProductSync", "FakeFirestoreProductDataSource generated products=${products.size}")
        return products
    }
}
