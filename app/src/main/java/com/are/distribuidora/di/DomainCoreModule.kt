package com.are.distribuidora.di

import android.content.Context
import com.are.distribuidora.data.core.AndroidConnectivityChecker
import com.are.distribuidora.data.core.AndroidLogger
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainCoreModule {

    @Binds
    @Singleton
    abstract fun bindLogger(impl: AndroidLogger): Logger

    companion object {
        @Provides
        @Singleton
        fun provideConnectivityChecker(@ApplicationContext context: Context): ConnectivityChecker {
            return AndroidConnectivityChecker(context)
        }
    }
}
