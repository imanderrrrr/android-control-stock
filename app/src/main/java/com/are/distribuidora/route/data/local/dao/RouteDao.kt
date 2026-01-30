package com.are.distribuidora.route.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.route.data.local.entity.RouteEntity

@Dao
interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Query("UPDATE routes SET synced = :synced WHERE id = :routeId")
    suspend fun markSynced(routeId: String, synced: Boolean)

    @Query("SELECT * FROM routes WHERE synced = 0 ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getPendingSync(limit: Int): List<RouteEntity>
}
