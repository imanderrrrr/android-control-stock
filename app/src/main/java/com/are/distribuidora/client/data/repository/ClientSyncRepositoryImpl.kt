package com.are.distribuidora.client.data.repository

import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import javax.inject.Inject

/**
 * Implementación DATA del contrato de dominio [ClientSyncRepository].
 *
 * Reglas:
 * - Puede usar DTOs/Room/Firestore/mapping.
 * - NO decide flujos (online/offline), NO hace try/catch de negocio, NO retorna Result.
 */
class ClientSyncRepositoryImpl @Inject constructor(
    private val remote: ClientRemoteDataSource,
    private val local: ClientLocalDataSource,
    private val mapper: ClientRemoteMapper,
) : ClientSyncRepository {

    override suspend fun fetchRemoteClients(): List<Client> {
        // Dominio no incluye routeId; se sincroniza solo para persistencia local.
        return remote.fetchClients(limit = 50).map { mapper.toDomain(it) }
    }

    override suspend fun saveLocalClients(clients: List<Client>) {
        // API legacy: no trae routeId. Mantener compatibilidad.
        local.upsert(clients.map { mapper.toEntity(it) })
    }

    /**
     * Método interno: cuando el remoto ya trae routeId, persistimos el vínculo en Room.
     */
    suspend fun saveLocalClientsFromRemote(dtos: List<com.are.distribuidora.client.data.remote.dto.ClientDto>) {
        local.upsert(dtos.map { mapper.toEntity(it) })
    }
}
