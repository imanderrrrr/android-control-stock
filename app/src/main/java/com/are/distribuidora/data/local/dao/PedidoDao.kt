package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.PedidoEntity
import com.are.distribuidora.data.local.entity.PedidoItemEntity
import com.are.distribuidora.data.local.entity.PedidoWithItemsEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface PedidoDao {

    // 6) Listar por cliente — excluye eliminados (isDeleted = 1) y PENDING_DELETE
    @Query("SELECT * FROM pedidos WHERE clienteId = :clienteId AND isDeleted = 0 AND syncStatus != 'PENDING_DELETE' ORDER BY updatedAt DESC")
    suspend fun getPedidosByCliente(clienteId: String): List<PedidoEntity>

    /**
     * Busca un pedido activo (no cancelado/fallido) que comparta el mismo orderKey.
     * Un orderKey activo bloquea la creación de un segundo pedido equivalente.
     * Estados "activos": PENDING_CREATE, PENDING_UPDATE, PENDING, SYNCING, SYNCED.
     * Estados que NO bloquean: FAILED, ERROR, PENDING_DELETE (pedido en proceso de eliminación).
     */
    @Query("""
        SELECT * FROM pedidos
        WHERE orderKey = :orderKey
          AND syncStatus NOT IN ('FAILED', 'ERROR', 'PENDING_DELETE')
        LIMIT 1
    """)
    suspend fun findActivePedidoByOrderKey(orderKey: String): PedidoEntity?

    /**
     * Regla de negocio: 1 pedido por cliente por día de creación.
     *
     * Busca si ya existe un pedido activo para [clienteId] cuyo [creadoEn] cae dentro
     * del mismo día calendario ([startOfDayMs] .. [endOfDayMs] en epoch-millis).
     *
     * Estados que bloquean: todos excepto FAILED, ERROR y PENDING_DELETE.
     * [isDeleted] = 0 excluye pedidos ya eliminados por el usuario o por el worker de expiración.
     */
    @Query("""
        SELECT * FROM pedidos
        WHERE clienteId = :clienteId
          AND creadoEn >= :startOfDayMs
          AND creadoEn <= :endOfDayMs
          AND isDeleted = 0
          AND syncStatus NOT IN ('FAILED', 'ERROR', 'PENDING_DELETE')
        LIMIT 1
    """)
    suspend fun findActivePedidoByClienteAndDay(
        clienteId: String,
        startOfDayMs: Long,
        endOfDayMs: Long,
    ): PedidoEntity?

    @Transaction
    @Query("SELECT * FROM pedidos WHERE clienteId = :clienteId ORDER BY updatedAt DESC")
    suspend fun getPedidosWithItemsByCliente(clienteId: String): List<PedidoWithItemsEntity>

    // --- FIX: Added insert and internal sync methods ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PedidoEntity)

    @Query("UPDATE pedidos SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPedidoSyncingInternal(id: String, status: SyncStatus, updatedAt: Long)

    @Query("UPDATE pedidos SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPedidoSyncedInternal(id: String, status: SyncStatus, updatedAt: Long)

    @Query("UPDATE pedidos SET syncStatus = :targetStatus, updatedAt = :updatedAt WHERE id = :id AND syncStatus = :currentStatus")
    suspend fun revertPedidoSyncingInternal(id: String, targetStatus: SyncStatus, currentStatus: SyncStatus, updatedAt: Long)

    // --- End FIX ---
    
    // --- FIX: Added pending pedidos queries ---
    @Query("SELECT * FROM pedidos WHERE syncStatus IN (:statuses) AND isDeleted = 0 ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingPedidos(statuses: List<SyncStatus>, limit: Int): List<PedidoEntity>

    @Transaction
    @Query("SELECT * FROM pedidos WHERE syncStatus IN (:statuses) AND isDeleted = 0 ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingPedidosWithItems(statuses: List<SyncStatus>, limit: Int): List<PedidoWithItemsEntity>
    
    @Query("SELECT * FROM pedidos WHERE clienteId IS NULL ORDER BY updatedAt DESC")
    suspend fun getPedidosSinCliente(): List<PedidoEntity>

    /** Obtiene todos los pedidos en estado SYNCING para recuperación al inicio. */
    @Query("SELECT * FROM pedidos WHERE syncStatus = :status")
    suspend fun getPedidosBySyncStatus(status: SyncStatus): List<PedidoEntity>

    /** Obtiene un pedido por su ID (cualquier estado). */
    @Query("SELECT * FROM pedidos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PedidoEntity?

    /** Obtiene todos los pedidos con sus items para la pantalla de Pedidos (agrupado por ruta).
     *  Excluye PENDING_DELETE e isDeleted: pedidos que el usuario ya eliminó no deben aparecer en la lista. */
    @Transaction
    @Query("SELECT * FROM pedidos WHERE syncStatus != 'PENDING_DELETE' AND isDeleted = 0 ORDER BY routeId ASC, creadoEn DESC")
    suspend fun getAllWithItems(): List<PedidoWithItemsEntity>

    /**
     * Versión reactiva de [getAllWithItems]: Room re-emite automáticamente cada vez que
     * se modifica cualquier fila de `pedidos` o `pedido_items` (incluyendo syncStatus).
     * Esto permite que la UI refleje los cambios PENDING → SYNCING → SYNCED sin recargar.
     */
    @Transaction
    @Query("SELECT * FROM pedidos WHERE syncStatus != 'PENDING_DELETE' AND isDeleted = 0 ORDER BY routeId ASC, creadoEn DESC")
    fun observeAllWithItems(): Flow<List<PedidoWithItemsEntity>>

    /**
     * Versión reactiva filtrada por fecha de entrega (formato "YYYY-MM-DD").
     * Permite al usuario ver solo los pedidos del día seleccionado.
     * Cuando [deliveryDate] está vacío actúa igual que [observeAllWithItems].
     */
    @Transaction
    @Query("""
        SELECT * FROM pedidos
        WHERE syncStatus != 'PENDING_DELETE'
          AND isDeleted = 0
          AND (:deliveryDate = '' OR deliveryDate = :deliveryDate)
        ORDER BY routeId ASC, creadoEn DESC
    """)
    fun observeAllWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItemsEntity>>

    /** Soft delete explícito: marca isDeleted=1 y syncStatus=PENDING_DELETE para que el
     *  worker no intente subir el pedido, y la UI lo oculte inmediatamente. */
    @Query("UPDATE pedidos SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPedidoDeleted(id: String, updatedAt: Long)
    // --- End FIX ---

    /** Marca el pedido como PENDING_UPDATE (edición offline pendiente de sync). */
    @Query("UPDATE pedidos SET syncStatus = 'PENDING_UPDATE', subtotal = :subtotal, total = :total, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPedidoPendingUpdate(id: String, subtotal: Double, total: Double, updatedAt: Long)

    /** Obtiene un pedido completo con ítems activos (isDeleted=0) para la UI de edición. */
    @Transaction
    @Query("SELECT * FROM pedidos WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getWithItemsById(id: String): PedidoWithItemsEntity?

    /** Versión reactiva de [getWithItemsById] para observar cambios en tiempo real. */
    @Transaction
    @Query("SELECT * FROM pedidos WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun observeWithItemsById(id: String): Flow<PedidoWithItemsEntity?>

    // --- End Editar Pedido ---

    @Transaction
    suspend fun markPedidoSyncing(id: String, updatedAt: Long) {
        markPedidoSyncingInternal(id, SyncStatus.SYNCING, updatedAt)
    }

    @Transaction
    suspend fun markPedidoSynced(id: String, updatedAt: Long) {
        markPedidoSyncedInternal(id, SyncStatus.SYNCED, updatedAt)
    }

    @Transaction
    suspend fun revertPedidoSyncingToPending(id: String, updatedAt: Long) {
        // Por defecto revertimos a PENDING_UPDATE (seguro ante falta de lastSyncedAt)
        revertPedidoSyncingInternal(id, SyncStatus.PENDING_UPDATE, SyncStatus.SYNCING, updatedAt)
    }

    @Transaction
    suspend fun revertPedidoSyncingToPendingCreate(id: String, updatedAt: Long) {
        revertPedidoSyncingInternal(id, SyncStatus.PENDING_CREATE, SyncStatus.SYNCING, updatedAt)
    }

    @Delete
    suspend fun delete(entity: PedidoEntity)

    /** Marca el pedido como PENDING_DELETE localmente (soft delete para SYNCED). */
    @Query("UPDATE pedidos SET syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAsPendingDelete(id: String, updatedAt: Long)

    /** Elimina físicamente el pedido local (para PENDING_CREATE no subido aún). */
    @Query("DELETE FROM pedidos WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Devuelve todos los pedidos cuyo [creadoEn] es anterior a [thresholdEpochMillis].
     * Usado por el worker de expiración para detectar pedidos con ≥ 2 semanas de antigüedad.
     * Solo considera pedidos visibles (no ya marcados como borrados).
     */
    @Transaction
    @Query("""
        SELECT * FROM pedidos
        WHERE creadoEn < :thresholdEpochMillis
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY creadoEn ASC
    """)
    suspend fun getExpiredPedidosWithItems(thresholdEpochMillis: Long): List<PedidoWithItemsEntity>

    // ── Report queries ────────────────────────────────────────────────────────

    /**
     * Total de ventas (suma de total) en el rango de fechas indicado.
     * Solo pedidos activos (no eliminados, no PENDING_DELETE).
     */
    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM pedidos
        WHERE creadoEn >= :sinceEpoch
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
    """)
    suspend fun sumTotalSince(sinceEpoch: Long): Double

    /**
     * Cantidad de pedidos en el rango.
     */
    @Query("""
        SELECT COUNT(*) FROM pedidos
        WHERE creadoEn >= :sinceEpoch
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
    """)
    suspend fun countPedidosSince(sinceEpoch: Long): Int

    /**
     * Desglose de ventas por ruta: routeId + suma total + conteo de pedidos.
     */
    @Query("""
        SELECT routeId, SUM(total) AS totalVentas, COUNT(*) AS pedidoCount
        FROM pedidos
        WHERE creadoEn >= :sinceEpoch
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
        GROUP BY routeId
        ORDER BY totalVentas DESC
    """)
    suspend fun salesByRouteSince(sinceEpoch: Long): List<RouteSalesTuple>

    /**
     * Ventas agrupadas por día (deliveryDate YYYY-MM-DD) para gráfica de líneas.
     */
    @Query("""
        SELECT deliveryDate AS dateLabel, SUM(total) AS totalVentas, COUNT(*) AS pedidoCount
        FROM pedidos
        WHERE creadoEn >= :sinceEpoch
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
          AND deliveryDate != ''
        GROUP BY deliveryDate
        ORDER BY deliveryDate ASC
    """)
    suspend fun salesByDaySince(sinceEpoch: Long): List<DailySalesTuple>

    /**
     * Top productos más solicitados (por cantidad total vendida).
     */
    @Query("""
        SELECT i.productoId, i.nombre AS productName,
               SUM(i.cantidad) AS totalQty,
               SUM(i.totalItem) AS totalRevenue
        FROM pedido_items i
        INNER JOIN pedidos p ON i.pedidoId = p.id
        WHERE p.creadoEn >= :sinceEpoch
          AND p.isDeleted = 0
          AND p.syncStatus != 'PENDING_DELETE'
          AND i.isDeleted = 0
        GROUP BY i.productoId
        ORDER BY totalQty DESC
        LIMIT :limit
    """)
    suspend fun topProductsSince(sinceEpoch: Long, limit: Int): List<TopProductTuple>

    /**
     * Productos menos solicitados (por cantidad total vendida, ascendente).
     */
    @Query("""
        SELECT i.productoId, i.nombre AS productName,
               SUM(i.cantidad) AS totalQty,
               SUM(i.totalItem) AS totalRevenue
        FROM pedido_items i
        INNER JOIN pedidos p ON i.pedidoId = p.id
        WHERE p.creadoEn >= :sinceEpoch
          AND p.isDeleted = 0
          AND p.syncStatus != 'PENDING_DELETE'
          AND i.isDeleted = 0
        GROUP BY i.productoId
        ORDER BY totalQty ASC
        LIMIT :limit
    """)
    suspend fun bottomProductsSince(sinceEpoch: Long, limit: Int): List<TopProductTuple>

    /**
     * Top clientes por monto total comprado.
     */
    @Query("""
        SELECT clienteNombre, COALESCE(clienteId, '') AS clienteId,
               SUM(total) AS totalSpent,
               COUNT(*) AS orderCount
        FROM pedidos
        WHERE creadoEn >= :sinceEpoch
          AND isDeleted = 0
          AND syncStatus != 'PENDING_DELETE'
        GROUP BY clienteNombre
        ORDER BY totalSpent DESC
        LIMIT :limit
    """)
    suspend fun topClientsSince(sinceEpoch: Long, limit: Int): List<TopClientTuple>
}

@Dao
interface PedidoItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PedidoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PedidoItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PedidoItemEntity)

    @Update
    suspend fun update(entity: PedidoItemEntity)

    @Query("SELECT * FROM pedido_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PedidoItemEntity?

    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId ORDER BY createdAt ASC")
    suspend fun getItemsByPedidoId(pedidoId: String): List<PedidoItemEntity>

    /** Solo ítems activos (no eliminados) de un pedido — usado en payload de sync y UI. */
    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getActiveItemsByPedidoId(pedidoId: String): List<PedidoItemEntity>

    /** Soft delete de un ítem: lo marca como eliminado sin borrarlo físicamente. */
    @Query("UPDATE pedido_items SET isDeleted = 1, syncStatus = 'PENDING_UPDATE', updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun markItemDeleted(itemId: String, updatedAt: Long)

    // 1) Items pendientes globales
    @Query("SELECT * FROM pedido_items WHERE syncStatus IN (:statuses) ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingItems(statuses: List<SyncStatus>, limit: Int): List<PedidoItemEntity>

    // 2) Items pendientes por pedido
    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId AND syncStatus IN (:statuses) ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingItemsForPedido(pedidoId: String, statuses: List<SyncStatus>, limit: Int): List<PedidoItemEntity>

    // 3) Marcar SYNCING (Internal)
    @Query("UPDATE pedido_items SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun markItemSyncingInternal(itemId: String, status: SyncStatus, updatedAt: Long)

    // 4) Marcar SYNCED (Internal)
    @Query("UPDATE pedido_items SET syncStatus = :syncedStatus, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun markItemSyncedInternal(itemId: String, syncedStatus: SyncStatus, updatedAt: Long)

    // 5) Marcar PENDING_DELETE (Internal)
    @Query("UPDATE pedido_items SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun markItemAsPendingDelete(itemId: String, status: SyncStatus, updatedAt: Long)

    // 6) Revertir SYNCING (Internal)
    @Query("UPDATE pedido_items SET syncStatus = :targetStatus, updatedAt = :updatedAt WHERE id = :itemId AND syncStatus = :currentStatus")
    suspend fun revertItemSyncingInternal(itemId: String, targetStatus: SyncStatus, currentStatus: SyncStatus, updatedAt: Long)

    @Transaction
    suspend fun markItemSyncing(itemId: String, updatedAt: Long) {
        markItemSyncingInternal(itemId, SyncStatus.SYNCING, updatedAt)
    }

    @Transaction
    suspend fun markItemSynced(itemId: String, updatedAt: Long) {
        markItemSyncedInternal(itemId, SyncStatus.SYNCED, updatedAt)
    }

    @Transaction
    suspend fun revertItemSyncingToPending(itemId: String, updatedAt: Long) {
        // Por defecto revertimos a PENDING_UPDATE
        revertItemSyncingInternal(itemId, SyncStatus.PENDING_UPDATE, SyncStatus.SYNCING, updatedAt)
    }

    @Transaction
    suspend fun revertItemSyncingToPendingCreate(itemId: String, updatedAt: Long) {
        revertItemSyncingInternal(itemId, SyncStatus.PENDING_CREATE, SyncStatus.SYNCING, updatedAt)
    }

    @Query("SELECT * FROM pedido_items")
    suspend fun getAll(): List<PedidoItemEntity>

    /** Obtiene todos los items de un pedido en estado SYNCING para recuperación al inicio. */
    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId AND syncStatus = :status")
    suspend fun getItemsBySyncStatus(pedidoId: String, status: SyncStatus): List<PedidoItemEntity>

    @Delete
    suspend fun delete(entity: PedidoItemEntity)

    /** Elimina físicamente todos los items de un pedido (usado al eliminar un pedido). */
    @Query("DELETE FROM pedido_items WHERE pedidoId = :pedidoId")
    suspend fun deleteByPedidoId(pedidoId: String)
}

/** Resultado de desglose de ventas por ruta. */
data class RouteSalesTuple(
    val routeId: String,
    val totalVentas: Double,
    val pedidoCount: Int,
)

/** Resultado de ventas diarias. */
data class DailySalesTuple(
    val dateLabel: String,
    val totalVentas: Double,
    val pedidoCount: Int,
)

/** Resultado de top productos. */
data class TopProductTuple(
    val productoId: String,
    val productName: String,
    val totalQty: Int,
    val totalRevenue: Double,
)

/** Resultado de top clientes. */
data class TopClientTuple(
    val clienteNombre: String,
    val clienteId: String,
    val totalSpent: Double,
    val orderCount: Int,
)

