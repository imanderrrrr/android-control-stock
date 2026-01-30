package com.are.distribuidora.di

import com.are.distribuidora.route.domain.repository.RouteRepository
import com.are.distribuidora.route.domain.repository.RouteSyncRepository
import com.are.distribuidora.route.domain.usecase.AssignClientToRouteUseCase
import com.are.distribuidora.route.domain.usecase.CreateRouteUseCase
import com.are.distribuidora.route.domain.usecase.DownloadRoutesUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import com.are.distribuidora.route.domain.usecase.SyncPendingRoutesUseCase
import com.are.distribuidora.route.domain.usecase.UpdateRouteDeliveryDayUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RouteModule {

    @Provides
    fun provideCreateRouteUseCase(repository: RouteRepository): CreateRouteUseCase =
        CreateRouteUseCase(repository)

    @Provides
    fun provideGetRoutesUseCase(repository: RouteRepository): GetRoutesUseCase =
        GetRoutesUseCase(repository)

    @Provides
    fun provideUpdateRouteDeliveryDayUseCase(repository: RouteRepository): UpdateRouteDeliveryDayUseCase =
        UpdateRouteDeliveryDayUseCase(repository)

    @Provides
    fun provideAssignClientToRouteUseCase(repository: RouteRepository): AssignClientToRouteUseCase =
        AssignClientToRouteUseCase(repository)

    @Provides
    fun provideSyncPendingRoutesUseCase(repository: RouteSyncRepository): SyncPendingRoutesUseCase =
        SyncPendingRoutesUseCase(repository)

    @Provides
    fun provideDownloadRoutesUseCase(repository: RouteSyncRepository): DownloadRoutesUseCase =
        DownloadRoutesUseCase(repository)
}
