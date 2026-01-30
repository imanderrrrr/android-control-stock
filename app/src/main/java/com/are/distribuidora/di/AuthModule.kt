package com.are.distribuidora.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.are.distribuidora.application.appstart.DecideStartDestinationUseCase
import com.are.distribuidora.auth.data.local.AuthLocalDataSource
import com.are.distribuidora.auth.data.local.DataStoreAuthLocalDataSource
import com.are.distribuidora.auth.data.remote.AuthRemoteDataSource
import com.are.distribuidora.auth.data.remote.FirebaseAuthRemoteDataSource
import com.are.distribuidora.auth.data.repository.DataStoreSessionRepository
import com.are.distribuidora.auth.data.repository.OfflineFirstAuthRepository
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.auth.domain.repository.SessionRepository
import com.are.distribuidora.auth.domain.usecase.CanRunSyncUseCase
import com.are.distribuidora.auth.domain.usecase.ObserveSessionUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.authDataStore

    @Provides
    @Singleton
    fun provideAuthLocalDataSource(dataStore: DataStore<Preferences>): AuthLocalDataSource =
        DataStoreAuthLocalDataSource(dataStore)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideAuthRemoteDataSource(firebaseAuth: FirebaseAuth): AuthRemoteDataSource =
        FirebaseAuthRemoteDataSource(firebaseAuth)

    @Provides
    @Singleton
    fun provideAuthRepository(
        local: AuthLocalDataSource,
        remote: AuthRemoteDataSource,
    ): AuthRepository = OfflineFirstAuthRepository(local = local, remote = remote)

    @Provides
    @Singleton
    fun provideSessionRepository(local: AuthLocalDataSource): SessionRepository =
        DataStoreSessionRepository(local)

    @Provides
    fun provideCanRunSyncUseCase(sessionRepository: SessionRepository): CanRunSyncUseCase =
        CanRunSyncUseCase(sessionRepository)

    @Provides
    fun provideObserveSessionUseCase(sessionRepository: SessionRepository): ObserveSessionUseCase =
        ObserveSessionUseCase(sessionRepository)

    @Provides
    fun provideDecideStartDestinationUseCase(
        observeSessionUseCase: ObserveSessionUseCase,
    ): DecideStartDestinationUseCase = DecideStartDestinationUseCase(observeSessionUseCase)
}
