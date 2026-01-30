package com.are.distribuidora.auth.domain.usecase

import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso de dominio: expone la observación de sesión sin filtrar detalles de infraestructura.
 *
 * Domain:
 * - No maneja estado/lifecycle.
 * - Solo depende de la abstracción [SessionRepository].
 */
class ObserveSessionUseCase(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): Flow<Session?> = sessionRepository.observeSession()
}
