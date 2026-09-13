package com.are.distribuidora.stockmovement.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {

    /**
     * IGNORE a propósito: el id de los movimientos automáticos es determinístico, así que un
     * segundo intento de registrar el mismo movimiento no duplica el asiento (idempotencia local).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<StockMovementEntity>): List<Long>

    @Query("SELECT * FROM stock_movements WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeByProduct(productId: String, limit: Int): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE orderId = :orderId ORDER BY createdAt ASC, id ASC")
    suspend fun getByOrderId(orderId: String): List<StockMovementEntity>

    /** Movimientos de un pedido que todavía no están en Firestore (viajan con el pedido). */
    @Query("SELECT * FROM stock_movements WHERE orderId = :orderId AND syncStatus != 'SYNCED' ORDER BY createdAt ASC, id ASC")
    suspend fun getUnsyncedByOrderId(orderId: String): List<StockMovementEntity>

    /**
     * Pendientes que NO van a viajar con un pedido: vales manuales (orderId NULL) y automáticos
     * cuyo pedido ya no está en cola de subida (borrado localmente o ya SYNCED).
     */
    @Query(
        """
        SELECT * FROM stock_movements
        WHERE syncStatus IN ('PENDING_CREATE', 'PENDING', 'FAILED')
          AND (
            orderId IS NULL
            OR orderId NOT IN (
              SELECT id FROM pedidos WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'SYNCING')
            )
          )
          AND (
            orderId IS NULL
            OR orderId NOT IN (SELECT orderId FROM orders WHERE pendingUpload = 1)
          )
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingStandalone(limit: Int): List<StockMovementEntity>

    /**
     * Suma con signo de los movimientos del producto que aún no llegaron al servidor. Se usa
     * para reconstruir el stock local como `stock remoto + pendientes` en cada downsync, de modo
     * que una venta sin subir no "desaparezca" cuando baja el contador viejo.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'ENTRADA' THEN quantity ELSE -quantity END), 0)
        FROM stock_movements
        WHERE productId = :productId AND syncStatus != 'SYNCED'
        """
    )
    suspend fun sumPendingDelta(productId: String): Int

    @Query("UPDATE stock_movements SET syncStatus = 'SYNCING' WHERE id IN (:ids) AND syncStatus != 'SYNCED'")
    suspend fun markSyncing(ids: List<String>)

    @Query("UPDATE stock_movements SET syncStatus = 'SYNCED', lastSyncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)

    @Query("UPDATE stock_movements SET syncStatus = 'PENDING_CREATE' WHERE id IN (:ids) AND syncStatus = 'SYNCING'")
    suspend fun revertSyncingToPending(ids: List<String>)

    /** Recuperación al arrancar: nada debe quedar atascado en SYNCING tras un crash. */
    @Query("UPDATE stock_movements SET syncStatus = 'PENDING_CREATE' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStaleSyncing()

    /**
     * ÚNICO borrado permitido: movimientos de un pedido que NUNCA llegó al servidor y se elimina
     * localmente. Como nada de ese pedido existe en Firestore, no hay nada que compensar.
     */
    @Query("DELETE FROM stock_movements WHERE orderId = :orderId AND syncStatus != 'SYNCED'")
    suspend fun deleteUnsyncedByOrderId(orderId: String)

    @Query("SELECT COUNT(*) FROM stock_movements WHERE syncStatus != 'SYNCED'")
    suspend fun countPending(): Int

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC")
    suspend fun getAll(): List<StockMovementEntity>
}
