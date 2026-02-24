package com.are.distribuidora.domain.product

import androidx.paging.PagingData
import com.are.distribuidora.domain.core.SyncState
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.ProductId
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getProductsStream(query: String?): Flow<PagingData<Product>>

    suspend fun getById(id: ProductId): Product?

    fun observeById(id: ProductId): Flow<Product?>

    suspend fun save(product: Product)

    suspend fun delete(id: String)

    fun getSyncStatuses(): Flow<Map<String, SyncState>>

    /**
     * Busca un producto activo por su código de barras.
     * Retorna null si no existe.
     */
    suspend fun findByBarcode(barcode: String): Product?

    /**
     * Suma [delta] unidades al stock del producto de forma transaccional.
     * Marca el producto como PENDING_UPDATE para sincronización.
     */
    suspend fun incrementStock(productId: String, delta: Int)
}
