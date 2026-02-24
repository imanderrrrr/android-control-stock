package com.are.distribuidora.di

import com.are.distribuidora.data.repository.ProductRepositoryImpl
import com.are.distribuidora.domain.product.ProductRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    abstract fun bindPedidoRepository(impl: com.are.distribuidora.data.repository.PedidoRepositoryImpl): com.are.distribuidora.domain.pedido.PedidoRepository
}
