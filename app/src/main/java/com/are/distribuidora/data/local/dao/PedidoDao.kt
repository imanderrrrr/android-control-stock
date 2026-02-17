package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.PedidoEntity
import com.are.distribuidora.data.local.entity.PedidoItemEntity

import com.are.distribuidora.data.local.SyncStatus

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

    // 1) Obtener por SyncStatus tipado (sin strings hardcodeados)
    @Query("SELECT * FROM pedidos WHERE syncStatus = :status ORDER BY updatedAt ASC")
    suspend fun getPedidosBySyncStatus(status: SyncStatus): List<PedidoEntity>

    // 2) Pendientes: Reutiliza la query tipada para evitar hardcode en SQL.
    // Se implementa como default method en la interfaz si Room lo permite (Kotlin),
    // o el caller debe usar getPedidosBySyncStatus(SyncStatus.PENDING).
    // Instrucción: "Implementa getPendingPedidos usando parámetro SyncStatus"
    // Solución: @Query filtrando por parámetro, y el wrapper pasa el valor.
    @androidx.room.Transaction
    suspend fun getPendingPedidos(): List<PedidoEntity> {
        return getPedidosBySyncStatus(SyncStatus.PENDING)
    }

    // 3) Update tipado
    @Query("UPDATE pedidos SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :pedidoId")
    suspend fun updatePedidoSyncStatus(pedidoId: String, status: SyncStatus, updatedAt: Long): Int

    @Delete
    suspend fun delete(entity: PedidoEntity)
}

// PedidoItemDao se mantiene aquí por convención (visto en archivo previo),
// pero eliminamos duplicados y ordenamos.
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

    // Método unificado y ordenado por createdAt
    @Query("SELECT * FROM pedido_items WHERE pedidoId = :pedidoId ORDER BY createdAt ASC")
    suspend fun getItemsByPedidoId(pedidoId: String): List<PedidoItemEntity>

    @Query("SELECT * FROM pedido_items")
    suspend fun getAll(): List<PedidoItemEntity>

    @Delete
    suspend fun delete(entity: PedidoItemEntity)
}

