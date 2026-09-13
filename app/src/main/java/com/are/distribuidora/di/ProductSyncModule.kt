package com.are.distribuidora.di

import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
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

    @Binds
    @Singleton
    abstract fun bindProductSyncCursorStore(
        impl: com.are.distribuidora.data.local.prefs.SharedPrefsProductSyncCursorStore,
    ): com.are.distribuidora.data.local.prefs.ProductSyncCursorStore
}

@Module
@InstallIn(SingletonComponent::class)
object ProductSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncProductsUseCase(
        repository: ProductSyncRepository,
        connectivityChecker: ConnectivityChecker,
        logger: Logger
    ): SyncProductsUseCase =
        SyncProductsUseCase(repository, connectivityChecker, logger)
}
