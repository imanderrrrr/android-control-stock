package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProductEntity): Long

    @Update
    suspend fun update(entity: ProductEntity)

    // Helper for upsert logic (try insert, if conflict (id exists) -> update)
    // But since we have specific sync logic, we might not need generic upsert.
    // Keeping simple insert/update is better for control.

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProductEntity?>

    @Query("""
        SELECT * FROM products 
        WHERE isDeleted = 0 
        AND (:query IS NULL OR name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun productsPagingSource(query: String?): androidx.paging.PagingSource<Int, ProductEntity>


    // --- SYNC METHODS ---

    @Query(
        """
        SELECT * FROM products
        WHERE syncStatus IN (:statuses)
        ORDER BY updatedAt DESC
        LIMIT :limit
    """
    )
    suspend fun getPending(
        statuses: List<com.are.distribuidora.data.local.SyncStatus>,
        limit: Int
    ): List<ProductEntity>

    @Query("SELECT id, syncStatus, isActive FROM products WHERE isDeleted = 0")
    fun getSyncStatuses(): Flow<List<com.are.distribuidora.data.local.model.ProductSyncStatusTuple>>

    @Query("UPDATE products SET syncStatus = :status WHERE id = :id")
    suspend fun markSyncingInternal(
        id: String,
        status: com.are.distribuidora.data.local.SyncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCING
    )

    @Query(
        """
        UPDATE products
        SET syncStatus = :syncedStatus,
            lastSyncedAt = :lastSyncedAt
        WHERE id = :id
    """
    )
    suspend fun markSyncedInternal(
        id: String,
        lastSyncedAt: Long,
        syncedStatus: com.are.distribuidora.data.local.SyncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED,
    )

    @Query("UPDATE products SET syncStatus = :status, isDeleted = 1 WHERE id = :id")
    suspend fun markAsPendingDelete(
        id: String,
        status: com.are.distribuidora.data.local.SyncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING_DELETE
    )

    @Query("SELECT MAX(updatedAt) FROM products WHERE syncStatus = 'SYNCED'")
    suspend fun getMaxUpdatedAtSynced(): Long?

    /**
     * Retorna el último producto sincronizado para usar como cursor compuesto (updatedAt, id).
     * Esto evita bucles infinitos si hay muchos productos con el mismo timestamp.
     */
    @Query("SELECT * FROM products WHERE syncStatus = 'SYNCED' OR syncStatus = 'CONFLICT' ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getLastSyncedProduct(): ProductEntity?

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteInternal(id: String)
    
    @androidx.room.Transaction
    suspend fun markSyncing(id: String) {
        markSyncingInternal(id)
    }

    @androidx.room.Transaction
    suspend fun markSynced(id: String, lastSyncedAt: Long) {
        markSyncedInternal(id, lastSyncedAt)
    }

    @Query("SELECT COUNT(*) FROM products WHERE isDeleted = 0")
    suspend fun countAll(): Int

    /**
     * Recupera productos que quedaron atrapados en estado SYNCING (por crash o kill de la app).
     *
     * Lógica de recuperación:
     * - Si isDeleted = 1 -> Estaba intentando borrarse -> PENDING_DELETE
     * - Si lastSyncedAt es NULL o 0 -> Nunca se sincronizó -> PENDING_CREATE
     * - Si lastSyncedAt > 0 -> Ya existía y se estaba editando -> PENDING_UPDATE
     */
    @Query("""
        UPDATE products
        SET syncStatus = CASE
            WHEN isDeleted = 1 THEN 'PENDING_DELETE'
            WHEN lastSyncedAt IS NULL OR lastSyncedAt = 0 THEN 'PENDING_CREATE'
            ELSE 'PENDING_UPDATE'
        END
        WHERE syncStatus = 'SYNCING'
    """)
    suspend fun resetStaleSyncingToPending()
    /**
     * Marca un producto en estado de conflicto.
     */
    @Query("UPDATE products SET syncStatus = 'CONFLICT' WHERE id = :id")
    suspend fun markAsConflict(id: String)

    /**
     * Inserta los datos del conflicto en la tabla auxiliar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(conflict: com.are.distribuidora.data.local.entity.ProductConflictEntity)

    @Query("SELECT COUNT(*) FROM products WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')")
    suspend fun countPending(): Int

    @androidx.room.Transaction
    suspend fun handleConflict(
        productId: String,
        conflict: com.are.distribuidora.data.local.entity.ProductConflictEntity
    ) {
        markAsConflict(productId)
        insertConflict(conflict)
    }

    /**
     * Revert inmediato por item: si un producto fue marcado como SYNCING pero el upload fall1 (red/HTTP/mapping/etc.),
     * lo devolvemos a su estado PENDING correspondiente.
     *
     * Reglas:
     * - Si isDeleted = 1 -> PENDING_DELETE
     * - Si lastSyncedAt es NULL o 0 -> PENDING_CREATE
     * - Si no -> PENDING_UPDATE
     *
     * Nota: el WHERE incluye syncStatus='SYNCING' para no pisar estados que pudieron cambiar concurrentemente.
     */
    @Query(
        """
        UPDATE products
        SET syncStatus = CASE
            WHEN isDeleted = 1 THEN 'PENDING_DELETE'
            WHEN lastSyncedAt IS NULL OR lastSyncedAt = 0 THEN 'PENDING_CREATE'
            ELSE 'PENDING_UPDATE'
        END
        WHERE id = :id
          AND syncStatus = 'SYNCING'
        """
    )
    suspend fun revertSyncingToPending(id: String)

    @Query("UPDATE products SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateImageUrl(id: String, imageUrl: String?)

    /**
     * Busca un producto activo por su código de barras.
     * Usado por el flujo "Agregar stock".
     */
    @Query("SELECT * FROM products WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    /**
     * Restaura [cantidad] unidades al stock disponible y reduce [comprometido] en la misma
     * cantidad (sin bajar de cero), deshaciendo un descuento previo.
     *
     * Lógica inversa de [deductStockAndCommit]:
     *  - stock'       = stock + cantidad
     *  - comprometido' = MAX(0, comprometido - cantidad)   → no baja de 0
     *
     * Ejemplos:
     *  - stock=0,  comprometido=8,  cantidad=8  → stock=8,  comprometido=0
     *  - stock=5,  comprometido=0,  cantidad=3  → stock=8,  comprometido=0
     *  - stock=0,  comprometido=3,  cantidad=10 → stock=10, comprometido=0
     */
    @Query("""
        UPDATE products
        SET stock        = stock + :cantidad,
            comprometido = MAX(0, comprometido - :cantidad)
        WHERE id = :id
    """)
    suspend fun restoreStock(id: String, cantidad: Int)

    /**
     * Descuenta [cantidad] del stock disponible e incrementa [comprometido] por la
     * diferencia que no pudo cubrirse con el stock restante.
     *
     * Lógica:
     *  - stockDescontado = MIN(stock, cantidad)     → lo que realmente se resta del stock
     *  - excedente       = cantidad - stockDescontado → lo que va a comprometido
     *
     * Ejemplos:
     *  - stock=10, cantidad=10 → stock=0, comprometido sin cambio
     *  - stock=2,  cantidad=10 → stock=0, comprometido += 8
     *  - stock=0,  cantidad=5  → stock=0, comprometido += 5
     */
    @Query("""
        UPDATE products
        SET stock        = stock - MIN(stock, :cantidad),
            comprometido = comprometido + MAX(0, :cantidad - stock)
        WHERE id = :id
    """)
    suspend fun deductStockAndCommit(id: String, cantidad: Int)
}
