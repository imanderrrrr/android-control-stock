package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.core.network.NetworkMonitor

/**
 * Caso de uso: sincronización descendente de clientes (remoto -> local).
 *
 * Reglas:
 * - Toda la lógica de flujo vive aquí (online/offline + try/catch + política de error).
 * - No usa DTOs ni entidades de base de datos.
 * - No importa Firestore/Room/Android/Hilt.
 */
class SyncClientsUseCase(
    private val repository: ClientSyncRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(): Result<Unit> {
        return if (networkMonitor.isOnline()) {
            try {
                val clients = repository.fetchRemoteClients()
                repository.saveLocalClients(clients)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.success(Unit)
        }
    }
}
