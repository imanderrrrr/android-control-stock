package com.are.distribuidora.di

import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.data.remote.ClientRemoteDataSourceImpl
import com.are.distribuidora.client.data.repository.ClientRepositoryImpl
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.data.local.DistribuidoraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClientRepositoryModule {

    @Provides
    @Singleton
    fun provideClientLocalDataSource(db: DistribuidoraDatabase): ClientLocalDataSource =
        ClientLocalDataSource(db.clientDao())

    @Provides
    @Singleton
    fun provideClientRemoteDataSource(firestore: com.google.firebase.firestore.FirebaseFirestore): ClientRemoteDataSource =
        ClientRemoteDataSourceImpl(firestore)

    @Provides
    @Singleton
    fun provideClientRepository(
        local: ClientLocalDataSource,
        remote: ClientRemoteDataSource,
        sync: ClientSyncRepository,
    ): ClientRepository =
        ClientRepositoryImpl(
            local = local,
            remote = remote,
            sync = sync,
        )
}
