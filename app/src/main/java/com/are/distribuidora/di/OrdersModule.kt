package com.are.distribuidora.di

import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.RoomOrderLocalDataSource
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import com.are.distribuidora.orders.domain.usecase.FetchOrdersHeaderUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrdersModule {

    @Provides
    @Singleton
    fun provideOrderLocalDataSource(
        db: DistribuidoraDatabase,
        orderDao: OrderDao,
        orderItemDao: OrderItemDao,
        stagingDao: OrderItemStagingDao,
    ): OrderLocalDataSource = RoomOrderLocalDataSource(
        db = db,
        orderDao = orderDao,
        orderItemDao = orderItemDao,
        stagingDao = stagingDao,
    )

    @Provides
    @Singleton
    fun provideOrderRepository(
        local: OrderLocalDataSource,
        remote: OrderRemoteDataSource,
    ): OrderRepository = OfflineFirstOrderRepository(
        local = local,
        remote = remote,
    )

    @Provides
    fun provideFetchOrdersHeaderUseCase(repository: OrderRepository): FetchOrdersHeaderUseCase =
        FetchOrdersHeaderUseCase(repository)

    @Provides
    fun provideDownloadOrderItemsUseCase(repository: OrderRepository): DownloadOrderItemsUseCase =
        DownloadOrderItemsUseCase(repository)
}
