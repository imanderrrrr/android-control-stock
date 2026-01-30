package com.are.distribuidora.debug

import com.are.distribuidora.domain.sale.CreateSaleUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint solo para debug.
 * Permite acceder a dependencias Hilt desde Application sin modificar la clase Application.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugSaleRunnerEntryPoint {
    fun createSaleUseCase(): CreateSaleUseCase
}
