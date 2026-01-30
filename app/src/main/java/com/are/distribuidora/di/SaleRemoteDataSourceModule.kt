package com.are.distribuidora.di

import com.are.distribuidora.data.remote.firestore.FirestoreSaleDataSource
import com.are.distribuidora.data.remote.sale.SaleRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings de data sources remotos de ventas.
 *
 * Mantiene Clean Architecture:
 * - Los consumidores dependen de la interfaz [SaleRemoteDataSource].
 * - La implementaci0n concreta se define en el data layer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SaleRemoteDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindSaleRemoteDataSource(
        impl: FirestoreSaleDataSource
    ): SaleRemoteDataSource
}
