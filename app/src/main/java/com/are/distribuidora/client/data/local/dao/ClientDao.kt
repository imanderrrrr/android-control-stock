package com.are.distribuidora.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ClientEntity): Long

    @androidx.room.Update
    suspend fun update(entity: ClientEntity)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?

    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getAll(limit: Int): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE routeId = :routeId AND isDeleted = 0 LIMIT :limit")
    suspend fun getByRouteId(routeId: String, limit: Int): List<ClientEntity>

    @Query("""
        SELECT * FROM clients 
        WHERE routeId = :routeId 
        AND syncStatus != 'PENDING_DELETE'
        AND isDeleted = 0
        ORDER BY updatedAt DESC 
        LIMIT :limit
    """)
    fun observeByRouteId(routeId: String, limit: Int): Flow<List<ClientEntity>>

    @Query("UPDATE clients SET routeId = :routeId WHERE id = :clientId")
    suspend fun updateRoute(clientId: String, routeId: String)

    @Query("SELECT COUNT(*) FROM clients WHERE routeId = :routeId AND isDeleted = 0")
    suspend fun countByRoute(routeId: String): Int

    @Query(
        """
        SELECT * FROM clients
        WHERE syncStatus IN (:statuses)
        ORDER BY updatedAt DESC
        LIMIT :limit
    """
    )
    suspend fun getPending(
        statuses: List<SyncStatus>,
        limit: Int
    ): List<ClientEntity>

    @Query("UPDATE clients SET syncStatus = :status, isDeleted = 1 WHERE id = :id")
    suspend fun markAsPendingDelete(
        id: String,
        status: SyncStatus = SyncStatus.PENDING_DELETE
    )

    @Query("SELECT MAX(updatedAt) FROM clients")
    suspend fun getMaxUpdatedAt(): Long?

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE clients SET syncStatus = :syncingStatus WHERE id = :id")
    suspend fun markSyncingInternal(id: String, syncingStatus: SyncStatus = SyncStatus.SYNCING)

    @Query("UPDATE clients SET syncStatus = :failedStatus WHERE id = :id")
    suspend fun markFailedInternal(id: String, failedStatus: SyncStatus = SyncStatus.FAILED)

    @Query(
        """
        UPDATE clients
        SET syncStatus = :syncedStatus,
            updatedAt = :updatedAt,
            lastSyncedAt = :lastSyncedAt
        WHERE id = :id
    """
    )
    suspend fun markSyncedInternal(
        id: String,
        updatedAt: Long,
        lastSyncedAt: Long,
        syncedStatus: SyncStatus = SyncStatus.SYNCED,
    )

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteInternal(id: String)

    @Query("UPDATE clients SET syncStatus = :pendingStatus WHERE id = :id")
    suspend fun markPendingInternal(id: String, pendingStatus: SyncStatus)

    @Transaction
    suspend fun markSyncing(id: String) {
        markSyncingInternal(id = id)
    }

    @Transaction
    suspend fun markFailed(id: String) {
        markFailedInternal(id = id)
    }

    @Transaction
    suspend fun markSynced(id: String, updatedAt: Long, lastSyncedAt: Long) {
        markSyncedInternal(id = id, updatedAt = updatedAt, lastSyncedAt = lastSyncedAt)
    }

    @Transaction
    suspend fun deleteInsideTransaction(id: String) {
        deleteInternal(id = id)
    }

    @Transaction
    suspend fun markPending(id: String, pendingStatus: SyncStatus = SyncStatus.PENDING_CREATE) {
        markPendingInternal(id = id, pendingStatus = pendingStatus)
    }
}
