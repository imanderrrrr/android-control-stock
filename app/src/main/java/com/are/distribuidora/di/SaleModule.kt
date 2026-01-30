package com.are.distribuidora.di

import com.are.distribuidora.domain.sale.CreateSaleUseCase

import com.are.distribuidora.domain.sale.SellProductUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SaleModule {

    @Provides
    fun provideCreateSaleUseCase(): CreateSaleUseCase = CreateSaleUseCase()


}
