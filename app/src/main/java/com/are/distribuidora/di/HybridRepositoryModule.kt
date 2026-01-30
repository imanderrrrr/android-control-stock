package com.are.distribuidora.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Módulo legacy.
 *
 * Antes proveía `HybridProductRepository` (repositorio híbrido con orquestación).
 * Tras el refactor, la sincronización se resuelve vía:
 * - `ProductSyncRepository` (data: I/O técnico)
 * - `SyncProductsUseCase` (dominio: flujo)
 */
@Module
@InstallIn(SingletonComponent::class)
object HybridRepositoryModule
