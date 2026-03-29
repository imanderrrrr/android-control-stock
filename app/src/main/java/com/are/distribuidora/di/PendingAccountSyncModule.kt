package com.are.distribuidora.di

import com.are.distribuidora.data.remote.firestore.FirestorePendingAccountDataSource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PendingAccountSyncModule {

    @Provides
    @Singleton
    fun provideFirestorePendingAccountDataSource(
        firestore: FirebaseFirestore,
    ): FirestorePendingAccountDataSource = FirestorePendingAccountDataSource(firestore)
}
