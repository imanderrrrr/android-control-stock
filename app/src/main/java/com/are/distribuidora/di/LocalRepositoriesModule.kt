package com.are.distribuidora.di

import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.dao.SaleDao
import com.are.distribuidora.data.local.dao.SaleItemDao
import com.are.distribuidora.data.repository.local.RoomSaleRepository

import com.are.distribuidora.domain.sale.SaleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Providers mínimos para repositorios locales usados internamente.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalRepositoriesModule {



    @Provides
    @Singleton
    fun provideSaleRepository(
        saleDao: SaleDao,
        saleItemDao: SaleItemDao,
    ): SaleRepository = RoomSaleRepository(saleDao, saleItemDao)
}
