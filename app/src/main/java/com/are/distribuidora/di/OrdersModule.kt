package com.are.distribuidora.di

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.auth.FirebaseCurrentUserIdProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.RoomOrderLocalDataSource
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.dao.OrderItemDao
import com.are.distribuidora.orders.data.local.dao.OrderItemStagingDao
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.data.repository.OfflineFirstOrderRepository
import com.are.distribuidora.orders.domain.repository.OrderRepository
import com.are.distribuidora.orders.domain.usecase.DeleteOrderUseCase
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import com.are.distribuidora.orders.domain.usecase.FetchAllOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.FetchOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.GetOtrosPedidoDetalleUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteAndDateUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteUseCase
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
    @Suppress("unused") // Hilt lo invoca por reflexión
    fun provideCurrentUserIdProvider(): CurrentUserIdProvider = FirebaseCurrentUserIdProvider()

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
        currentUserIdProvider: CurrentUserIdProvider,
    ): OrderRepository = OfflineFirstOrderRepository(
        local = local,
        remote = remote,
        currentUserIdProvider = currentUserIdProvider,
    )

    @Provides
    fun provideFetchOrdersHeaderUseCase(repository: OrderRepository): FetchOrdersHeaderUseCase =
        FetchOrdersHeaderUseCase(repository)

    @Provides
    fun provideFetchAllOrdersHeaderUseCase(repository: OrderRepository): FetchAllOrdersHeaderUseCase =
        FetchAllOrdersHeaderUseCase(repository)

    @Provides
    fun provideDownloadOrderItemsUseCase(repository: OrderRepository): DownloadOrderItemsUseCase =
        DownloadOrderItemsUseCase(repository)

    @Provides
    fun provideDeleteOrderUseCase(repository: OrderRepository): DeleteOrderUseCase =
        DeleteOrderUseCase(repository)

    @Provides
    fun provideObserveOtherOrdersByRouteUseCase(repository: OrderRepository): ObserveOtherOrdersByRouteUseCase =
        ObserveOtherOrdersByRouteUseCase(repository)

    @Provides
    fun provideObserveOtherOrdersByRouteAndDateUseCase(repository: OrderRepository): ObserveOtherOrdersByRouteAndDateUseCase =
        ObserveOtherOrdersByRouteAndDateUseCase(repository)

    @Provides
    fun provideGetOtrosPedidoDetalleUseCase(repository: OrderRepository): GetOtrosPedidoDetalleUseCase =
        GetOtrosPedidoDetalleUseCase(repository)
}
