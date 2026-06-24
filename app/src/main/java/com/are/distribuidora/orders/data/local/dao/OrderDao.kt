package com.are.distribuidora.orders.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OrderEntity)

    @Update
    suspend fun update(entity: OrderEntity)

    /** Obtiene por id sin filtrar isDeleted (para uso interno: soft delete, guard rails). */
    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getById(orderId: String): OrderEntity?

    /** Lista pública: excluye eliminados y pedidos propios. */
    @Query("SELECT * FROM orders WHERE routeId = :routeId AND deliveryDate = :deliveryDate AND isDeleted = 0")
    suspend fun getByRouteAndDate(routeId: String, deliveryDate: String): List<OrderEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE routeId = :routeId AND deliveryDate = :deliveryDate AND isDeleted = 0")
    suspend fun countByRouteAndDate(routeId: String, deliveryDate: String): Int

    /**
     * Soft delete de los headers propios acotado a routeId+deliveryDate+vendedorId.
     * Usa isDeleted=1 en lugar de DELETE físico para preservar pedidos ya COMPLETED
     * con sus items descargados, evitando pérdida de datos locales en cada sync.
     *
     * GUARD: `AND pendingUpload = 0` — nunca purgar un pedido con edición local sin subir.
     * Mientras pendingUpload=1 la edición local es la fuente de verdad hasta que el worker
     * la confirme en remoto; sin esta cláusula la purga la borraba antes de subirse.
     */
    @Query("UPDATE orders SET isDeleted = 1, updatedAt = :now WHERE routeId = :routeId AND deliveryDate = :deliveryDate AND vendedorId = :vendedorId AND pendingUpload = 0")
    suspend fun deleteOwnHeaders(routeId: String, deliveryDate: String, vendedorId: String, now: Long)

    /**
     * Soft delete de todos los headers propios de una ruta (sin filtro de fecha).
     * Usado por fetchAllOrdersHeader para purgar pedidos propios antes de re-sincronizar.
     *
     * GUARD: `AND pendingUpload = 0` — preserva ediciones locales sin subir (ver deleteOwnHeaders).
     */
    @Query("UPDATE orders SET isDeleted = 1, updatedAt = :now WHERE routeId = :routeId AND vendedorId = :vendedorId AND pendingUpload = 0")
    suspend fun deleteAllOwnHeadersByRoute(routeId: String, vendedorId: String, now: Long)

    /** Soft delete: marca isDeleted=1 y actualiza updatedAt. */
    @Query("UPDATE orders SET isDeleted = 1, updatedAt = :now WHERE orderId = :orderId")
    suspend fun markDeleted(orderId: String, now: Long)

    /**
     * Pedidos con ediciones locales pendientes de subir a Firestore.
     * Excluye eliminados. Lo consume el worker de subida (OrdersUploadWorker).
     */
    @Query("SELECT * FROM orders WHERE pendingUpload = 1 AND isDeleted = 0")
    suspend fun getPendingUpload(): List<OrderEntity>

    /** Marca/limpia el flag de edición local pendiente de subir. */
    @Query("UPDATE orders SET pendingUpload = :pending, updatedAt = :now WHERE orderId = :orderId")
    suspend fun setPendingUpload(orderId: String, pending: Boolean, now: Long)

    /**
     * Flow reactivo: emite cada vez que cambia algún order de la ruta.
     * Excluye eliminados. No filtra vendedorId (el repositorio aplica el filtro en memoria).
     */
    @Query("SELECT * FROM orders WHERE routeId = :routeId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeByRoute(routeId: String): Flow<List<OrderEntity>>

    /**
     * Flow reactivo filtrado por ruta Y fecha de entrega (YYYY-MM-DD).
     * Excluye eliminados. El repositorio aplica el filtro Opción B (vendedorId) en memoria.
     */
    @Query("""
        SELECT * FROM orders
        WHERE routeId = :routeId
          AND deliveryDate = :deliveryDate
          AND isDeleted = 0
        ORDER BY updatedAt DESC
    """)
    fun observeByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<OrderEntity>>
}
