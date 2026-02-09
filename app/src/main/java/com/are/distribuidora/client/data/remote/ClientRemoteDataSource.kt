package com.are.distribuidora.client.data.remote

import com.are.distribuidora.client.data.remote.dto.ClientDto

interface ClientRemoteDataSource {
    suspend fun uploadClient(client: ClientDto)
    suspend fun softDeleteClient(id: String)
    suspend fun fetchClients(limit: Int = 50): List<ClientDto>
    suspend fun fetchClientsAfter(timestamp: Long): List<ClientDto>
    suspend fun getClientById(id: String): ClientDto?
}
