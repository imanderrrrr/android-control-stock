package com.are.distribuidora.core.di

import com.are.distribuidora.data.storage.FirebaseProductImageStorage
import com.are.distribuidora.data.storage.ProductImageStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProductImageStorageModule {

    @Binds
    abstract fun bindProductImageStorage(impl: FirebaseProductImageStorage): ProductImageStorage
}

