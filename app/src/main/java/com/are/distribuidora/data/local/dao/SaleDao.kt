package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.SaleEntity
import com.are.distribuidora.data.local.entity.SaleItemEntity

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SaleEntity)

    @Update
    suspend fun update(entity: SaleEntity)

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SaleEntity?

    @Query("SELECT * FROM sales")
    suspend fun getAll(): List<SaleEntity>

    @Delete
    suspend fun delete(entity: SaleEntity)
}

@Dao
interface SaleItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SaleItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SaleItemEntity)

    @Update
    suspend fun update(entity: SaleItemEntity)

    @Query("SELECT * FROM sale_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SaleItemEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getBySaleId(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items")
    suspend fun getAll(): List<SaleItemEntity>

    @Delete
    suspend fun delete(entity: SaleItemEntity)
}
