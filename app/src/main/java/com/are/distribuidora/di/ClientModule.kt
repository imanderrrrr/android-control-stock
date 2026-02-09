package com.are.distribuidora.di

import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.domain.usecase.CreateClientUseCase
import com.are.distribuidora.client.domain.usecase.DeleteClientUseCase
import com.are.distribuidora.client.domain.usecase.GetClientByIdUseCase
import com.are.distribuidora.client.domain.usecase.GetClientsUseCase
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.core.network.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ClientModule {

    @Provides
    fun provideCreateClientUseCase(
        clientRepository: ClientRepository,
        routeRepository: com.are.distribuidora.route.domain.repository.RouteRepository,
        authRepository: AuthRepository,
    ): CreateClientUseCase =
        CreateClientUseCase(clientRepository, routeRepository, authRepository)

    @Provides
    fun provideGetClientsUseCase(repository: ClientRepository): GetClientsUseCase =
        GetClientsUseCase(repository)

    @Provides
    fun provideGetClientByIdUseCase(repository: ClientRepository): GetClientByIdUseCase =
        GetClientByIdUseCase(repository)

    @Provides
    fun provideDeleteClientUseCase(repository: ClientRepository): DeleteClientUseCase =
        DeleteClientUseCase(repository)

    @Provides
    fun provideSyncClientsUseCase(
        repository: ClientSyncRepository,
        routeSyncRepository: com.are.distribuidora.route.domain.repository.RouteSyncRepository,
        networkMonitor: NetworkMonitor,
    ): SyncClientsUseCase =
        SyncClientsUseCase(repository, routeSyncRepository, networkMonitor)
}
