package com.are.distribuidora.di

import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.usecase.ExpirePedidosUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para las dependencias del borrado por antigüedad de pedidos.
 *
 * - Provee [ExpirePedidosUseCase] (inyectado en [PedidoExpireWorker]).
 * - [PedidoExpireScheduler] es @Singleton con @Inject constructor, no necesita
 *   provisión manual.
 */
@Module
@InstallIn(SingletonComponent::class)
object PedidoExpireModule {

    @Provides
    @Singleton
    fun provideExpirePedidosUseCase(
        repository: PedidoRepository,
        connectivityChecker: ConnectivityChecker,
        logger: Logger,
    ): ExpirePedidosUseCase = ExpirePedidosUseCase(repository, connectivityChecker, logger)
}

