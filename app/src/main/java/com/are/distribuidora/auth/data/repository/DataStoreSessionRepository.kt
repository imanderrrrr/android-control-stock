package com.are.distribuidora.auth.data.repository

import com.are.distribuidora.auth.data.local.AuthLocalDataSource
import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementación de infraestructura para sesión.
 *
 * Nota: el data source actual solo expone lectura puntual (suspend getSession).
 * Para cumplir el contrato de observación sin agregar frameworks, exponemos un Flow frío
 * que lee el estado actual. Si luego evolucionamos el data source a Flow (DataStore),
 * se puede reemplazar fácilmente.
 */
class DataStoreSessionRepository(
    private val local: AuthLocalDataSource,
) : SessionRepository {

    override suspend fun saveSession(session: Session) {
        local.saveSession(session)
    }

    override suspend fun clearSession() {
        local.clearSession()
    }

    override fun observeSession(): Flow<Session?> = flow {
        emit(local.getSession())
    }
}
