package com.are.distribuidora.client.fakes

import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.data.remote.dto.ClientDto

class FakeClientRemoteDataSource : ClientRemoteDataSource {
    
    var uploadCalls = 0
    internal val remoteStorage = mutableMapOf<String, ClientDto>()

    // Compat: algunos tests legacy acceden a `store`
    val store: MutableMap<String, ClientDto>
        get() = remoteStorage

    override suspend fun uploadClient(client: ClientDto) {
        uploadCalls++
        remoteStorage[client.id] = client
    }

    override suspend fun deleteClient(id: String) {
        remoteStorage.remove(id)
    }

    override suspend fun fetchClients(limit: Int): List<ClientDto> {
        return remoteStorage.values.toList().take(limit)
    }

    override suspend fun getClientById(id: String): ClientDto? {
        return remoteStorage[id]
    }
}
