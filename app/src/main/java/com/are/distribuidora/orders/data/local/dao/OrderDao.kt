package com.are.distribuidora.orders.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.orders.data.local.entity.OrderEntity

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OrderEntity)

    @Update
    suspend fun update(entity: OrderEntity)

    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getById(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE routeId = :routeId AND deliveryDate = :deliveryDate")
    suspend fun getByRouteAndDate(routeId: String, deliveryDate: String): List<OrderEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE routeId = :routeId AND deliveryDate = :deliveryDate")
    suspend fun countByRouteAndDate(routeId: String, deliveryDate: String): Int
}
