package com.are.distribuidora.di

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.screenaccess.data.local.dao.ScreenAccessDao
import com.are.distribuidora.screenaccess.data.remote.FirestoreScreenAccessDataSource
import com.are.distribuidora.screenaccess.data.repository.ScreenAccessRepositoryImpl
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import com.are.distribuidora.screenaccess.domain.usecase.ObserveScreenAccessUseCase
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring del control de acceso por pantalla (`userScreenAccess/{uid}`).
 * `CurrentUserIdProvider` ya lo provee [OrdersModule]; aquí solo lo consumimos.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScreenAccessModule {

    @Provides
    @Singleton
    fun provideFirestoreScreenAccessDataSource(
        firestore: FirebaseFirestore,
    ): FirestoreScreenAccessDataSource = FirestoreScreenAccessDataSource(firestore)

    @Provides
    @Singleton
    fun provideScreenAccessRepository(
        dao: ScreenAccessDao,
        remote: FirestoreScreenAccessDataSource,
        currentUserIdProvider: CurrentUserIdProvider,
    ): ScreenAccessRepository = ScreenAccessRepositoryImpl(
        dao = dao,
        remote = remote,
        currentUserIdProvider = currentUserIdProvider,
    )

    @Provides
    fun provideObserveScreenAccessUseCase(
        repository: ScreenAccessRepository,
    ): ObserveScreenAccessUseCase = ObserveScreenAccessUseCase(repository)
}
