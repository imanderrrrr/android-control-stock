package com.are.distribuidora.screenaccess.domain.usecase

import com.are.distribuidora.screenaccess.domain.model.ScreenAccess
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observa los permisos de pantalla del usuario actual (default-allow aplicado).
 */
class ObserveScreenAccessUseCase @Inject constructor(
    private val repository: ScreenAccessRepository,
) {
    operator fun invoke(): Flow<ScreenAccess> = repository.observeScreenAccess()
}
