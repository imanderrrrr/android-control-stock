package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.route.domain.repository.RouteSyncRepository

/**
 * Descarga rutas desde el remoto y las persiste localmente.
 * El UseCase delega la política de sincronización al repository.
 */
class DownloadRoutesUseCase(
    private val repository: RouteSyncRepository,
) {
    suspend operator fun invoke() {
        repository.pullRemote()
    }
}
