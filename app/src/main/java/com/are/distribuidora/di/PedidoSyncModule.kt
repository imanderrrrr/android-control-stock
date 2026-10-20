package com.are.distribuidora.di

import com.are.distribuidora.data.remote.firestore.FirestorePedidoDataSource
import com.are.distribuidora.data.remote.pedido.PedidoRemoteDataSource
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.usecase.SyncPedidosUseCase
import com.are.distribuidora.workers.ProductSyncScheduler
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para las dependencias de sync de pedidos.
 *
 * - Provee [PedidoRemoteDataSource] → [FirestorePedidoDataSource].
 * - Provee [SyncPedidosUseCase].
 *
 * NOTA: [PedidoRepository] ya está vinculado en [RepositoryModule].
 *       [FirebaseFirestore] ya está provisto en [RemoteModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PedidoSyncBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPedidoRemoteDataSource(
        impl: FirestorePedidoDataSource
    ): PedidoRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object PedidoSyncUseCaseModule {

    @Provides
    @Singleton
    fun provideSyncPedidosUseCase(
        repository: PedidoRepository,
        connectivityChecker: ConnectivityChecker,
        logger: Logger,
        productSyncScheduler: ProductSyncScheduler,
    ): SyncPedidosUseCase = SyncPedidosUseCase(
        repository = repository,
        connectivityChecker = connectivityChecker,
        logger = logger,
        // Una venta subida ya descontó el stock en el servidor: bajar el catálogo trae
        // también lo que vendieron los demás teléfonos.
        requestCatalogRefresh = { productSyncScheduler.scheduleOneTimeNow("PEDIDO_UPLOADED") },
    )
}

