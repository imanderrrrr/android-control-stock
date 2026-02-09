package com.are.distribuidora.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.data.local.dao.PedidoDao
import com.are.distribuidora.data.local.dao.PedidoItemDao
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.dao.SaleDao
import com.are.distribuidora.data.local.dao.SaleItemDao
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.data.local.entity.PedidoEntity
import com.are.distribuidora.data.local.entity.PedidoItemEntity
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.local.entity.SaleEntity
import com.are.distribuidora.data.local.entity.SaleItemEntity
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.route.data.local.dao.RouteDao
import com.are.distribuidora.route.data.local.entity.RouteEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PedidoEntity::class,
        PedidoItemEntity::class,
        ClientEntity::class,
        // Routes
        RouteEntity::class,
        // Orders (nuevo módulo)
        OrderEntity::class,
        OrderItemEntity::class,
        OrderItemStagingEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
abstract class DistribuidoraDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun pedidoItemDao(): PedidoItemDao
    abstract fun clientDao(): ClientDao

    // Routes
    abstract fun routeDao(): RouteDao

    // Orders
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun orderItemStagingDao(): OrderItemStagingDao

    /**
     * Wrapper para ejecutar operaciones en una transacción.
     * Evita que los repositorios dependan de extensiones de RoomDatabase en llamadas externas.
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R = withTransaction { block() }
}
