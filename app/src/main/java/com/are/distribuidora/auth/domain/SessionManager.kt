package com.are.distribuidora.auth.domain

/**
 * DEPRECATED (Clean Architecture): esta clase ya no debe vivir en Domain.
 *
 * Se reemplazó por:
 * - Contrato: com.are.distribuidora.auth.domain.repository.SessionRepository
 * - Regla: com.are.distribuidora.auth.domain.usecase.CanRunSyncUseCase
 */
@Deprecated(
    message = "No usar. SessionManager fue removido de Domain. Usa SessionRepository/UseCases.",
    level = DeprecationLevel.ERROR,
)
class SessionManager
