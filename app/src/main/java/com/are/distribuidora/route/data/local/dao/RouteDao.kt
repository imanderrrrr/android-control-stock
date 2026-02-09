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

    @Query("SELECT COUNT(*) FROM routes")
    suspend fun countRoutes(): Int

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY name ASC LIMIT :limit")
    suspend fun getAll(limit: Int): List<RouteEntity>

    @Query("UPDATE routes SET deliveryDay = :deliveryDay, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDeliveryDay(id: String, deliveryDay: Int, updatedAt: Long)

    @Query("UPDATE routes SET syncStatus = :syncStatus WHERE id = :routeId")
    suspend fun markSynced(routeId: String, syncStatus: SyncStatus)

    @Query("SELECT * FROM routes WHERE syncStatus = :pendingStatus ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingSync(pendingStatus: SyncStatus, limit: Int): List<RouteEntity>
}
