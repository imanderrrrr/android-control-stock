package com.are.distribuidora.application.appstart

import com.are.distribuidora.auth.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Caso de uso de capa aplicación (orquestación): traduce el estado de sesión a un destino
 * de arranque.
 *
 * Importante:
 * - No expone Session a la UI.
 * - No depende de repositorios: solo de casos de uso de dominio.
 */
class DecideStartDestinationUseCase(
    private val observeSessionUseCase: ObserveSessionUseCase,
) {
    operator fun invoke(): Flow<StartDestination> {
        return observeSessionUseCase()
            .map { session ->
                if (session != null) StartDestination.Home else StartDestination.Login
            }
    }
}
