package com.are.distribuidora.di

import com.are.distribuidora.domain.product.AddStockToProductUseCase
import com.are.distribuidora.domain.product.FindProductByBarcodeUseCase
import com.are.distribuidora.domain.product.ObserveProductsUseCase
import com.are.distribuidora.domain.product.ProductRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ProductModule {

    @Provides
    fun provideObserveProductsUseCase(repository: ProductRepository): ObserveProductsUseCase =
        ObserveProductsUseCase(repository)

    @Provides
    fun provideObserveProductByIdUseCase(repository: ProductRepository): com.are.distribuidora.domain.product.ObserveProductByIdUseCase =
        com.are.distribuidora.domain.product.ObserveProductByIdUseCase(repository)

    @Provides
    fun provideFindProductByBarcodeUseCase(repository: ProductRepository): FindProductByBarcodeUseCase =
        FindProductByBarcodeUseCase(repository)

    @Provides
    fun provideAddStockToProductUseCase(repository: ProductRepository): AddStockToProductUseCase =
        AddStockToProductUseCase(repository)
}
