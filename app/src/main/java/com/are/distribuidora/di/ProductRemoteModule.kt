package com.are.distribuidora.di

import com.are.distribuidora.data.remote.firestore.FirestoreProductDataSource
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binding para el data source remoto de productos.
 *
 * - Producción (main): usa FirestoreProductDataSource (REAL)
 * - Tests: siguen pudiendo usar FakeFirestoreProductDataSource instanciándolo directamente,
 *   o pueden reemplazar este binding en androidTest si fuese necesario.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProductRemoteModule {

    @Provides
    @Singleton
    fun provideProductRemoteDataSource(firestore: FirebaseFirestore): ProductRemoteDataSource =
        FirestoreProductDataSource(firestore)
}
