package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.are.distribuidora.data.local.entity.PedidoDraftEntity
import com.are.distribuidora.data.local.entity.PedidoDraftItemEntity
import com.are.distribuidora.data.local.entity.PedidoDraftWithItemsEntity

/**
 * Acceso al borrador del pedido en curso. Tabla puramente local.
 */
@Dao
interface PedidoDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: PedidoDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PedidoDraftItemEntity>)

    @Query("DELETE FROM pedido_draft_items WHERE vendedorId = :vendedorId")
    suspend fun deleteItems(vendedorId: String)

    @Query("DELETE FROM pedido_draft WHERE vendedorId = :vendedorId")
    suspend fun deleteDraft(vendedorId: String)

    @Transaction
    @Query("SELECT * FROM pedido_draft WHERE vendedorId = :vendedorId LIMIT 1")
    suspend fun getDraft(vendedorId: String): PedidoDraftWithItemsEntity?

    @Query("SELECT COUNT(*) FROM pedido_draft WHERE vendedorId = :vendedorId")
    suspend fun countFor(vendedorId: String): Int

    /**
     * Reemplaza cabecera e ítems en UNA transacción.
     *
     * Borra los ítems antes de reinsertarlos porque el carrito es un mapa completo,
     * no un delta: así un producto quitado desaparece de verdad del borrador.
     * Todo dentro de la misma transacción para que un cierre a mitad de escritura
     * no deje una cabecera con los ítems de la versión anterior.
     */
    @Transaction
    suspend fun replaceDraft(draft: PedidoDraftEntity, items: List<PedidoDraftItemEntity>) {
        upsertDraft(draft)
        deleteItems(draft.vendedorId)
        if (items.isNotEmpty()) insertItems(items)
    }
}
