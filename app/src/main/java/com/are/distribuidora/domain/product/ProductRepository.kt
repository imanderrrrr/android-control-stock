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

    /**
     * Devuelve el número total de productos almacenados localmente.
     * Usado por la capa de dominio sin filtrar a Presentation.
     */
    suspend fun countAll(): Int

    fun getSyncStatuses(): Flow<Map<String, SyncState>>

    /**
     * Busca un producto activo por su código de barras.
     * Retorna null si no existe.
     */
    suspend fun findByBarcode(barcode: String): Product?

    /**
     * LEGACY 4.0: sumaba [delta] al contador directamente. Desde 4.1 el stock SOLO se mueve
     * a través del libro de movimientos (vales); ver CreateStockVoucherUseCase.
     */
    @Deprecated("Usar CreateStockVoucherUseCase (vale de entrada). El stock solo se mueve por movimientos.")
    suspend fun incrementStock(productId: String, delta: Int)

    /** Búsqueda por nombre/categoría/código para elegir producto (p. ej. en "Nuevo vale"). */
    suspend fun searchByName(query: String, limit: Int = 20): List<Product> = emptyList()
}
