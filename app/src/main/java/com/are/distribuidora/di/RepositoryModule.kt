package com.are.distribuidora.di

import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.repository.ProductRepositoryImpl
import com.are.distribuidora.data.repository.local.LocalProductRepository
import com.are.distribuidora.domain.product.ProductRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {



    @Provides
    fun provideProductRepository(impl: ProductRepositoryImpl): ProductRepository = impl
}
