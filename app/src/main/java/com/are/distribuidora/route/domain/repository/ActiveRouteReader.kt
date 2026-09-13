package com.are.distribuidora.route.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Lectura de la ruta activa del vendedor (la que eligió para su jornada en Inicio).
 * Abstracción mínima para que ViewModels y casos de uso sean testeables sin DataStore.
 */
interface ActiveRouteReader {
    /** Id de la ruta activa solo si fue fijada en [today] (yyyy-MM-dd); si no, null. */
    fun observeActiveRouteId(today: String): Flow<String?>
}
