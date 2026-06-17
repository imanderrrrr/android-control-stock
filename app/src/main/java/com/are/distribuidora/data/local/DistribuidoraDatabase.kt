package com.are.distribuidora.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.are.distribuidora.data.local.entity.PendingUploadEntity
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
import com.are.distribuidora.screenaccess.data.local.dao.ScreenAccessDao
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PedidoEntity::class,
        PedidoItemEntity::class,
        ClientEntity::class,
        com.are.distribuidora.data.local.entity.ProductConflictEntity::class,
        // Routes
        RouteEntity::class,
        // Orders (nuevo módulo)
        OrderEntity::class,
        OrderItemEntity::class,
        OrderItemStagingEntity::class,
        // Upload queue
        PendingUploadEntity::class,
        // Pending accounts
        PendingAccountEntity::class,
        // Control de acceso por pantalla (panel web)
        ScreenAccessEntity::class,
    ],
    version = 36,
    exportSchema = true,
)
@androidx.room.TypeConverters(
    com.are.distribuidora.data.local.converter.SyncStatusConverters::class
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

    // Upload queue
    abstract fun pendingUploadDao(): PendingUploadDao

    // Pending accounts
    abstract fun pendingAccountDao(): PendingAccountDao

    // Control de acceso por pantalla
    abstract fun screenAccessDao(): ScreenAccessDao

    /**
     * Wrapper para ejecutar operaciones en una transacción.
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R = withTransaction { block() }
}
