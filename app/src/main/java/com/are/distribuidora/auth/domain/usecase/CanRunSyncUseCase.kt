package com.are.distribuidora.auth.domain.usecase

import com.are.distribuidora.auth.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first

/**
 * Regla de dominio: Sync puede correr solo si hay sesión y el authToken no es null/blank.
 *
 * Nota: no maneja estado ni persistencia, solo consulta el repositorio.
 */
class CanRunSyncUseCase(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(): Boolean {
        val session = sessionRepository.observeSession().first() ?: return false
        return !session.authToken.isNullOrBlank()
    }
}
