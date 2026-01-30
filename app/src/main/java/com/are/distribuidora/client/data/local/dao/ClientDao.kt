package com.are.distribuidora.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.client.data.local.entity.ClientEntity

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ClientEntity>)

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?

    @Query("SELECT * FROM clients LIMIT :limit")
    suspend fun getAll(limit: Int): List<ClientEntity>

    @Query("UPDATE clients SET routeId = :routeId WHERE id = :clientId")
    suspend fun updateRoute(clientId: String, routeId: String?)

    @Query("SELECT COUNT(*) FROM clients WHERE routeId = :routeId")
    suspend fun countByRoute(routeId: String): Int
}
