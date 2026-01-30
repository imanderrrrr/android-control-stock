package com.are.distribuidora.auth.domain.repository

import com.are.distribuidora.auth.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Abstracción de sesión para el dominio.
 *
 * Domain solo define el contrato: cómo guardar/limpiar/observar la sesión.
 * La persistencia, caché, DataStore/Firebase, etc. viven en Data/Infrastructure.
 */
interface SessionRepository {
    suspend fun saveSession(session: Session)

    suspend fun clearSession()

    /**
     * Observa cambios de sesión.
     *
     * - Emite null cuando no hay sesión.
     * - No asume lifecycle: el consumidor decide el scope.
     */
    fun observeSession(): Flow<Session?>
}
