package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.PedidoEntity
import com.are.distribuidora.data.local.entity.PedidoItemEntity

@Dao
interface PedidoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PedidoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PedidoEntity)

    @Update
    suspend fun update(entity: PedidoEntity)

    @Query("SELECT * FROM pedidos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PedidoEntity?

    @Query("SELECT * FROM pedidos")
    suspend fun getAll(): List<PedidoEntity>

    @Delete
    suspend fun delete(entity: PedidoEntity)
}

@Dao
interface PedidoItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PedidoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PedidoItemEntity)

    @Update
    suspend fun update(entity: PedidoItemEntity)

    @Query("SELECT * FROM pedido_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PedidoItemEntity?

    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId")
    suspend fun getByPedidoId(pedidoId: String): List<PedidoItemEntity>

    @Query("SELECT * FROM pedido_items")
    suspend fun getAll(): List<PedidoItemEntity>

    @Delete
    suspend fun delete(entity: PedidoItemEntity)
}

