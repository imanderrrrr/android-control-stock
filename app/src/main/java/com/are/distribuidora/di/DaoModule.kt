package com.are.distribuidora.di

import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.dao.SaleDao
import com.are.distribuidora.data.local.dao.SaleItemDao
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.route.data.local.dao.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideProductDao(db: DistribuidoraDatabase): ProductDao = db.productDao()

    @Provides
    fun provideSaleDao(db: DistribuidoraDatabase): SaleDao = db.saleDao()

    @Provides
    fun provideSaleItemDao(db: DistribuidoraDatabase): SaleItemDao = db.saleItemDao()

    @Provides
    fun providePedidoDao(db: DistribuidoraDatabase): com.are.distribuidora.data.local.dao.PedidoDao = db.pedidoDao()

    @Provides
    fun providePedidoItemDao(db: DistribuidoraDatabase): com.are.distribuidora.data.local.dao.PedidoItemDao = db.pedidoItemDao()

    // Orders
    @Provides
    fun provideOrderDao(db: DistribuidoraDatabase): OrderDao = db.orderDao()

    @Provides
    fun provideOrderItemDao(db: DistribuidoraDatabase): OrderItemDao = db.orderItemDao()

    @Provides
    fun provideOrderItemStagingDao(db: DistribuidoraDatabase): OrderItemStagingDao = db.orderItemStagingDao()

    // Routes
    @Provides
    fun provideRouteDao(db: DistribuidoraDatabase): RouteDao = db.routeDao()
    
    @Provides
    fun provideClientDao(db: DistribuidoraDatabase): ClientDao = db.clientDao()

    @Provides
    fun providePendingUploadDao(db: DistribuidoraDatabase): PendingUploadDao = db.pendingUploadDao()

    @Provides
    fun providePendingAccountDao(db: DistribuidoraDatabase): PendingAccountDao = db.pendingAccountDao()
}
