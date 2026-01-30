package com.are.distribuidora.orders.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity

@Dao
interface OrderItemStagingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OrderItemStagingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<OrderItemStagingEntity>)

    @Query("SELECT * FROM order_items_staging WHERE orderId = :orderId")
    suspend fun getByOrderId(orderId: String): List<OrderItemStagingEntity>

    @Query("SELECT COUNT(*) FROM order_items_staging WHERE orderId = :orderId")
    suspend fun countByOrderId(orderId: String): Int

    @Query("DELETE FROM order_items_staging WHERE orderId = :orderId")
    suspend fun deleteByOrderId(orderId: String)
}
