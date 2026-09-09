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

    /**
     * Marca SYNCED tras una subida exitosa.
     *
     * GUARD anti lost-update: solo transiciona si la fila SIGUE en SYNCING. Si entre
     * markSyncing y este punto el usuario editó el producto (quedando PENDING_UPDATE),
     * NO debemos pisar ese flag: la edición concurrente debe sobrevivir y volver a subirse
     * en el siguiente ciclo. Sin este `AND syncStatus = 'SYNCING'` la edición se perdía
     * silenciosamente (el worker subió la copia vieja y borraba el pending de la nueva).
     */
    @Query(
        """
        UPDATE products
        SET syncStatus = :syncedStatus,
            lastSyncedAt = :lastSyncedAt,
            updatedAt = COALESCE(:serverUpdatedAt, updatedAt),
            stock = COALESCE(:stock, stock)
        WHERE id = :id
          AND syncStatus = 'SYNCING'
    """
    )
    suspend fun markSyncedInternal(
        id: String,
        lastSyncedAt: Long,
        serverUpdatedAt: Long?,
        stock: Int?,
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

    /**
     * Marca SYNCED tras subir. [serverUpdatedAt] es el `updatedAt` que el servidor asignó al doc
     * (serverTimestamp): adoptarlo evita que el cursor de bajada (MAX(updatedAt) de los SYNCED)
     * se envenene con un reloj local adelantado y deje de recibir cambios (hallazgo 1 de la
     * auditoría 2026-09-03; mismo patrón que ClientSyncRepositoryImpl). [stock] es el contador
     * remoto vigente + movimientos locales pendientes; null = conservar el local.
     */
    @androidx.room.Transaction
    suspend fun markSynced(id: String, lastSyncedAt: Long, serverUpdatedAt: Long? = null, stock: Int? = null) {
        markSyncedInternal(id, lastSyncedAt, serverUpdatedAt, stock)
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
     * Alias de updateImageUrl — actualiza imageUrl con la URL remota tras subir a Storage.
     * Mantiene compatibilidad con código existente que llamaba updateImageRemoteUrl.
     */
    @Query("UPDATE products SET imageUrl = :imageRemoteUrl WHERE id = :id")
    suspend fun updateImageRemoteUrl(id: String, imageRemoteUrl: String?)

    @Query("UPDATE products SET imageLocalUri = :imageLocalUri WHERE id = :id")
    suspend fun updateImageLocalUri(id: String, imageLocalUri: String?)

    /**
     * Busca un producto activo por su código de barras.
     * Usado por el flujo "Agregar stock" / "Nuevo vale".
     */
    @Query("SELECT * FROM products WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    /** Búsqueda por nombre/categoría/código para elegir producto en "Nuevo vale". */
    @Query("""
        SELECT * FROM products
        WHERE isDeleted = 0
          AND (name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
        LIMIT :limit
    """)
    suspend fun searchByName(query: String, limit: Int): List<ProductEntity>

    /**
     * ÚNICA escritura del contador de stock desde 4.1.
     *
     * Aplica el efecto con signo de un movimiento del libro (`stock_movements`): +cantidad para
     * ENTRADA, -cantidad para SALIDA. Se permite quedar en negativo (decisión #6 del plan).
     *
     * A propósito NO toca `syncStatus` ni `updatedAt`: el teléfono ya no sube `stock` como valor
     * absoluto (el sync de productos solo sube campos descriptivos), así que mover el contador no
     * "ensucia" el producto. Lo que viaja al servidor es el movimiento, con un incremento
     * atómico en la misma transacción; el downsync reconstruye el stock local como
     * `stock remoto + movimientos pendientes` (ver ProductSyncRepositoryImpl).
     */
    @Query("UPDATE products SET stock = stock + :delta WHERE id = :id")
    suspend fun applyMovement(id: String, delta: Int)

    /**
     * Sobrescribe el contador local con un valor reconstruido (stock remoto + pendientes).
     * Solo lo usa el sync; nunca la UI ni los pedidos.
     */
    @Query("UPDATE products SET stock = :stock WHERE id = :id")
    suspend fun setStockFromSync(id: String, stock: Int)
}
