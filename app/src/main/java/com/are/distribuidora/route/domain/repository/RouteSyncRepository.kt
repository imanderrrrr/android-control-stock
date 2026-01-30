package com.are.distribuidora.route.domain.repository

/**
 * Contrato de dominio: sincronización ascendente de rutas pendientes (local -> remoto) y descarga remota.
 *
 * No retorna Result: la política de errores vive en el UseCase.
 */
interface RouteSyncRepository {
    suspend fun syncPending(limit: Int = 100)

    // Nueva operación: descargar rutas desde el remoto y reemplazar las locales.
    suspend fun pullRemote()
}
