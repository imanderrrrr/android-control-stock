package com.are.distribuidora.di

import com.google.firebase.firestore.FirebaseFirestore
import com.are.distribuidora.data.remote.firestore.FirestoreSaleDataSource
import com.are.distribuidora.orders.data.remote.FirestoreOrderRemoteDataSource
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirestoreSaleDataSource(firestore: FirebaseFirestore): FirestoreSaleDataSource =
        FirestoreSaleDataSource(firestore)

    @Provides
    @Singleton
    fun provideOrderRemoteDataSource(firestore: FirebaseFirestore): OrderRemoteDataSource =
        FirestoreOrderRemoteDataSource(firestore)
}
