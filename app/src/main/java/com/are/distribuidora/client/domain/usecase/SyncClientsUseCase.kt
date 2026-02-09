package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.core.network.NetworkMonitor
import kotlinx.coroutines.sync.Mutex

/**
 * Caso de uso: sincronización bidireccional de clientes (local <-> remoto).
 *
 * Reglas:
 * - Toda la lógica de flujo vive aquí (online/offline + try/catch + política de error).
 * - No usa DTOs ni entidades de base de datos.
 * - No importa Firestore/Room/Android/Hilt.
 */
class SyncClientsUseCase(
    private val repository: ClientSyncRepository,
    private val routeSyncRepository: com.are.distribuidora.route.domain.repository.RouteSyncRepository,
    private val networkMonitor: NetworkMonitor,
) {

    private val syncMutex = Mutex()

    suspend operator fun invoke(): Result<Unit> {
        // Enfoque D (Startup & Concurrency): Verificación inicial de red.
        if (!networkMonitor.isOnline()) {
            return Result.success(Unit)
        }

        if (!syncMutex.tryLock()) {
            return Result.success(Unit)
        }

        try {
            // Regla FK Fail Fast Absoluto:
            // 1. Sincronizar rutas PRIMERO.
            // 2. Si falla, ABORTAR todo. No intentar sincronizar clientes.
            try {
                routeSyncRepository.pullRemote()
                // También subimos rutas pendientes para mantener consistencia bidireccional, 
                // aunque el requisito estricto es "Download Routes BEFORE Clients".
                routeSyncRepository.syncPending() 
            } catch (e: Exception) {
                // Escenario C (Route Sync falla): Client Sync no ejecuta.
                // Log and Fail Fast.
                throw e
            }

            // 3. Si rutas OK, procedemos con clientes.
            try {
                repository.uploadPendingClients()
            } catch (_: Exception) {
                // Upload no debe abortar download, per se, pero si hay error grave de red, fallará el download después.
            }

            val remoteClients = repository.fetchRemoteClients()
            repository.saveLocalClients(remoteClients)

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            syncMutex.unlock()
        }
    }
}
