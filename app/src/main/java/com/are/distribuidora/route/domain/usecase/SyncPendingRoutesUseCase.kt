package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.route.domain.repository.RouteSyncRepository

/**
 * Sincroniza rutas pendientes (local -> Firestore).
 *
 * Nota: Este caso de uso no detecta conectividad.
 * La app puede llamarlo cuando detecte que hay internet (o en un botón / worker).
 */
class SyncPendingRoutesUseCase(
    private val repository: RouteSyncRepository,
) {
    suspend operator fun invoke(limit: Int = 100) {
        repository.syncPending(limit = limit)
    }
}
