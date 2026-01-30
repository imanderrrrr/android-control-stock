package com.are.distribuidora.presentation.app

import com.are.distribuidora.application.appstart.DecideStartDestinationUseCase
import com.are.distribuidora.application.appstart.StartDestination
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Orquestador pasivo de presentación para decidir el start destination.
 *
 * Reglas:
 * - No depende de repositorios.
 * - No conoce Session, tokens, ni detalles de auth.
 * - Solo delega en un caso de uso de capa aplicación.
 */
class AppStartDecider @Inject constructor(
    private val decideStartDestinationUseCase: DecideStartDestinationUseCase,
) {
    suspend fun decideStartDestination(): StartDestination {
        return decideStartDestinationUseCase().first()
    }
}
