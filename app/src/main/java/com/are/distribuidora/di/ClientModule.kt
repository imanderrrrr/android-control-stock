package com.are.distribuidora.di

import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.domain.usecase.CreateClientUseCase
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
    fun provideCreateClientUseCase(repository: ClientRepository): CreateClientUseCase =
        CreateClientUseCase(repository)

    @Provides
    fun provideGetClientsUseCase(repository: ClientRepository): GetClientsUseCase =
        GetClientsUseCase(repository)

    @Provides
    fun provideGetClientByIdUseCase(repository: ClientRepository): GetClientByIdUseCase =
        GetClientByIdUseCase(repository)

    @Provides
    fun provideSyncClientsUseCase(
        repository: ClientSyncRepository,
        networkMonitor: NetworkMonitor,
    ): SyncClientsUseCase =
        SyncClientsUseCase(repository, networkMonitor)
}
