package com.are.distribuidora.screenaccess.domain.usecase

import com.are.distribuidora.screenaccess.domain.model.UserAccess
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observa el acceso del usuario actual (rol + pantallas; mínimo privilegio por defecto).
 */
class ObserveScreenAccessUseCase @Inject constructor(
    private val repository: ScreenAccessRepository,
) {
    operator fun invoke(): Flow<UserAccess> = repository.observeUserAccess()
}
