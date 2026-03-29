package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.data.local.entity.PendingUploadEntity

@Dao
interface PendingUploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUploadEntity)

    /**
     * Obtiene el siguiente upload pendiente (PENDING o FAILED), ordenado por createdAt ASC.
     */
    @Query(
        """
        SELECT * FROM pending_uploads
        WHERE state IN ('PENDING', 'FAILED')
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getNextPending(limit: Int = 1): List<PendingUploadEntity>

    @Query("UPDATE pending_uploads SET state = 'UPLOADING' WHERE id = :id")
    suspend fun markUploading(id: String)

    @Query("UPDATE pending_uploads SET state = 'DONE' WHERE id = :id")
    suspend fun markDone(id: String)

    @Query(
        """
        UPDATE pending_uploads
        SET state = 'FAILED',
            lastError = :error,
            attemptCount = :attemptCount
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: String, error: String?, attemptCount: Int)

    /**
     * Elimina registros completados más antiguos que el timestamp dado.
     */
    @Query("DELETE FROM pending_uploads WHERE state = 'DONE' AND createdAt < :olderThan")
    suspend fun deleteDoneOlderThan(olderThan: Long)

    /**
     * Busca un PendingUpload existente para un entityId dado (para upsert/idempotencia).
     */
    @Query("SELECT * FROM pending_uploads WHERE entityId = :entityId AND entityType = 'PRODUCT' LIMIT 1")
    suspend fun findByEntityId(entityId: String): PendingUploadEntity?

    /**
     * Cuenta cuántos uploads están pendientes (PENDING o FAILED).
     */
    @Query("SELECT COUNT(*) FROM pending_uploads WHERE state IN ('PENDING', 'FAILED')")
    suspend fun countPending(): Int

    /**
     * Busca un PendingUpload existente por entityId y entityType.
     */
    @Query("SELECT * FROM pending_uploads WHERE entityId = :entityId AND entityType = :entityType LIMIT 1")
    suspend fun findByEntityIdAndType(entityId: String, entityType: String): PendingUploadEntity?
}

