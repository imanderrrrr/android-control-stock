package com.are.distribuidora.client.data.repository

import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import javax.inject.Inject

/**
 * LEGACY wrapper.
 *
 * Se mantiene para no romper consumidores viejos.
 *
 * Regla: no debe contener lógica de flujo (online/offline), try/catch de negocio ni mapping.
 */
@Deprecated("Legacy wrapper. Use SyncClientsUseCase")
class HybridClientRepository @Inject constructor(
    private val delegate: ClientSyncRepository,
) {
    suspend fun syncClients() {
        delegate.fetchRemoteClients()
            .also { delegate.saveLocalClients(it) }
    }
}
