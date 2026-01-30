package com.are.distribuidora.di

import com.are.distribuidora.client.data.repository.ClientSyncRepositoryImpl
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClientSyncModule {

    @Binds
    @Singleton
    abstract fun bindClientSyncRepository(
        impl: ClientSyncRepositoryImpl,
    ): ClientSyncRepository
}
