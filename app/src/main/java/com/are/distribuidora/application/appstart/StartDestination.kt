package com.are.distribuidora.application.appstart

/**
 * Resultado de la orquestación de arranque.
 *
 * Vive fuera de UI para que la navegación de arranque pueda decidirse sin que la UI conozca
 * Session/tokens/repositorios.
 */
sealed class StartDestination {
    data object Login : StartDestination()
    data object Home : StartDestination()
}
