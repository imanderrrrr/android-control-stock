package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingAccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingAccountEntity)

    @Update
    suspend fun update(entity: PendingAccountEntity)

    @Query("SELECT * FROM pending_accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingAccountEntity?

    /**
     * Todas las cuentas pendientes activas (no pagadas, no eliminadas), ordenadas:
     * 1. Vencidas primero (dueDateMillis < now), las más viejas arriba.
     * 2. Luego las próximas a vencer.
     */
    @Query("""
        SELECT * FROM pending_accounts
        WHERE isPaid = 0 AND resolvedAction IS NULL
        ORDER BY
            CASE WHEN dueDateMillis < :nowMillis THEN 0 ELSE 1 END ASC,
            dueDateMillis ASC
    """)
    fun observeAllActive(nowMillis: Long): Flow<List<PendingAccountEntity>>

    /** Cuenta pendientes activas (no pagadas, no eliminadas). */
    @Query("SELECT COUNT(*) FROM pending_accounts WHERE isPaid = 0 AND resolvedAction IS NULL")
    fun observeActiveCount(): Flow<Int>

    /** Marca como pagada con datos de quién lo hizo. */
    @Query("""
        UPDATE pending_accounts
        SET isPaid = 1,
            resolvedBy = :resolvedBy,
            resolvedAt = :resolvedAt,
            resolvedAction = 'PAID',
            syncStatus = 'PENDING_UPDATE',
            updatedAt = :resolvedAt
        WHERE id = :id
    """)
    suspend fun markPaid(id: String, resolvedAt: Long, resolvedBy: String)

    /** Soft-delete: marca como eliminada con datos de quién lo hizo. */
    @Query("""
        UPDATE pending_accounts
        SET resolvedBy = :resolvedBy,
            resolvedAt = :resolvedAt,
            resolvedAction = 'DELETED',
            syncStatus = 'PENDING_UPDATE',
            updatedAt = :resolvedAt
        WHERE id = :id
    """)
    suspend fun markDeleted(id: String, resolvedAt: Long, resolvedBy: String)

    /** Hard-delete. */
    @Query("DELETE FROM pending_accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Actualiza la URL remota de la foto de factura tras subir a Storage y marca para re-sync. */
    @Query("UPDATE pending_accounts SET invoiceRemoteUrl = :remoteUrl, updatedAt = :now, syncStatus = 'PENDING_UPDATE' WHERE id = :id")
    suspend fun updateInvoiceRemoteUrl(id: String, remoteUrl: String, now: Long)

    /**
     * Actividad reciente: cuentas pagadas o eliminadas en los últimos N días.
     * Ordenadas por fecha de resolución más reciente primero.
     */
    @Query("""
        SELECT * FROM pending_accounts
        WHERE resolvedAction IS NOT NULL
          AND resolvedAt > :sinceMillis
        ORDER BY resolvedAt DESC
    """)
    fun observeRecentActivity(sinceMillis: Long): Flow<List<PendingAccountEntity>>

    /**
     * Cuentas pendientes de sync (crear o actualizar en Firestore).
     */
    @Query("SELECT * FROM pending_accounts WHERE syncStatus IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING')")
    suspend fun getPendingSync(): List<PendingAccountEntity>

    /** Marca como sincronizada. */
    @Query("UPDATE pending_accounts SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    /** Upsert desde Firestore (para sync de bajada). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(entity: PendingAccountEntity)

    /** Todos los IDs locales. */
    @Query("SELECT id FROM pending_accounts")
    suspend fun getAllIds(): List<String>

    /**
     * Elimina físicamente los registros de actividad (pagados/eliminados)
     * cuya fecha de resolución sea anterior a [beforeMillis].
     * Se llama automáticamente al iniciar para limpiar logs de más de 3 días.
     */
    @Query("DELETE FROM pending_accounts WHERE resolvedAction IS NOT NULL AND resolvedAt < :beforeMillis")
    suspend fun deleteOldResolvedBefore(beforeMillis: Long)

    /** Observa una cuenta por id (para la pantalla de detalle en vivo). */
    @Query("SELECT * FROM pending_accounts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PendingAccountEntity?>

    /** Edita los campos de una cuenta existente y la marca para re-sincronizar. */
    @Query("""
        UPDATE pending_accounts
        SET routeId = :routeId,
            clientId = :clientId,
            clientName = :clientName,
            routeName = :routeName,
            amountCents = :amountCents,
            dueDateMillis = :dueDateMillis,
            notes = :notes,
            invoicePhotoUri = :invoicePhotoUri,
            invoiceRemoteUrl = :invoiceRemoteUrl,
            updatedAt = :now,
            syncStatus = 'PENDING_UPDATE'
        WHERE id = :id
    """)
    suspend fun updateDetails(
        id: String,
        routeId: String,
        clientId: String,
        clientName: String,
        routeName: String,
        amountCents: Long,
        dueDateMillis: Long,
        notes: String?,
        invoicePhotoUri: String?,
        invoiceRemoteUrl: String?,
        now: Long,
    )
}
