package com.are.distribuidora.route.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.are.distribuidora.data.local.SyncStatus

@Dao
interface RouteDao {

    @androidx.room.Upsert
    suspend fun upsert(entity: RouteEntity)

    @androidx.room.Upsert
    suspend fun upsertAll(entities: List<RouteEntity>)

    @Query("DELETE FROM routes")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM routes WHERE isDeleted = 0")
    suspend fun countRoutes(): Int

    @Query("SELECT * FROM routes WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    /** Obtiene una ruta por ID sin filtrar isDeleted (para sync). */
    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: String): RouteEntity?

    @Query("SELECT * FROM routes WHERE isDeleted = 0 ORDER BY name ASC LIMIT :limit")
    suspend fun getAll(limit: Int): List<RouteEntity>

    @Query("UPDATE routes SET deliveryDay = :deliveryDay, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDeliveryDay(id: String, deliveryDay: Int, updatedAt: Long)

    @Query("UPDATE routes SET syncStatus = :syncStatus WHERE id = :routeId")
    suspend fun markSynced(routeId: String, syncStatus: SyncStatus)

    @Query("SELECT * FROM routes WHERE syncStatus IN ('PENDING', 'PENDING_DELETE') ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int): List<RouteEntity>

    /** Soft-delete: marca como eliminada y pendiente de sync. */
    @Query("UPDATE routes SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Hard-delete: elimina físicamente rutas soft-deleted con más de N días. */
    @Query("DELETE FROM routes WHERE isDeleted = 1 AND updatedAt < :cutoffMillis")
    suspend fun hardDeleteOldSoftDeleted(cutoffMillis: Long)

    /** Hard-delete por ID. */
    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun hardDeleteById(id: String)
}
