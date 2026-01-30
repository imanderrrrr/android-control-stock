package com.are.distribuidora.di


import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.route.data.local.RouteLocalDataSource
import com.are.distribuidora.route.data.remote.FirestoreRouteRemoteDataSource
import com.are.distribuidora.route.data.remote.RouteRemoteDataSource
import com.are.distribuidora.route.data.repository.OfflineFirstRouteRepository
import com.are.distribuidora.route.data.repository.RouteSyncRepositoryImpl
import com.are.distribuidora.route.domain.repository.RouteRepository
import com.are.distribuidora.route.domain.repository.RouteSyncRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RouteRepositoryModule {

    @Provides
    @Singleton
    fun provideRouteLocalDataSource(
        db: DistribuidoraDatabase,
    ): RouteLocalDataSource =
        RouteLocalDataSource(
            db = db,
            routeDao = db.routeDao(),
            clientDao = db.clientDao(),
        )

    @Provides
    @Singleton
    fun provideRouteRemoteDataSource(firestore: FirebaseFirestore): RouteRemoteDataSource =
        FirestoreRouteRemoteDataSource(firestore)

    @Provides
    @Singleton
    fun provideRouteRepository(
        local: RouteLocalDataSource,
        remote: RouteRemoteDataSource,
    ): RouteRepository = OfflineFirstRouteRepository(local = local, remote = remote)

    @Provides
    @Singleton
    fun provideRouteSyncRepository(
        local: RouteLocalDataSource,
        remote: RouteRemoteDataSource,
    ): RouteSyncRepository = RouteSyncRepositoryImpl(local = local, remote = remote)
}
