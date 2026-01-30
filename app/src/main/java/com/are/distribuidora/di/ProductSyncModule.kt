package com.are.distribuidora.di

import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.product.ProductSyncRepository
import com.are.distribuidora.domain.product.SyncProductsUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProductSyncBindingsModule {

    @Binds
    @Singleton
    abstract fun bindProductSyncRepository(impl: ProductSyncRepositoryImpl): ProductSyncRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ProductSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncProductsUseCase(repository: ProductSyncRepository): SyncProductsUseCase =
        SyncProductsUseCase(repository)
}
