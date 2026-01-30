package com.are.distribuidora.client.domain.repository

import com.are.distribuidora.client.domain.model.Client

/**
 * Contrato PURO de sincronización (remoto -> local) para Clientes.
 *
 * Reglas:
 * - Solo usa modelos de dominio ([Client]).
 * - No retorna Result.
 * - No conoce Room/Firestore/DTOs/Mappers/Android/Hilt.
 */
interface ClientSyncRepository {
    suspend fun fetchRemoteClients(): List<Client>
    suspend fun saveLocalClients(clients: List<Client>)
}
