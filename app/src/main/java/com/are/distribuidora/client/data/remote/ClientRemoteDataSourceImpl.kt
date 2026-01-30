package com.are.distribuidora.client.data.remote

import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firestore de [ClientRemoteDataSource].
 * - Colección: "clients"
 * - Documento: id del cliente
 */
class ClientRemoteDataSourceImpl(
    private val firestore: FirebaseFirestore,
) : ClientRemoteDataSource {

    private val collection = firestore.collection("clients")

    override suspend fun uploadClient(client: ClientDto) {
        val payload = hashMapOf(
            "id" to client.id,
            "name" to client.name,
            "address" to client.address,
            "createdAt" to client.createdAt,
            "routeId" to client.routeId,
        )
        collection.document(client.id).set(payload).await()
    }

    override suspend fun fetchClients(limit: Int): List<ClientDto> {
        val snap = collection.limit(limit.toLong()).get().await()
        return snap.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name") ?: return@mapNotNull null
            val address = doc.getString("address")
            val createdAt = doc.getLong("createdAt") ?: 0L
            val routeId = doc.getString("routeId")
            ClientDto(id = id, name = name, address = address, createdAt = createdAt, routeId = routeId)
        }
    }

    override suspend fun getClientById(id: String): ClientDto? {
        val doc = collection.document(id).get().await()
        if (!doc.exists()) return null
        val name = doc.getString("name") ?: return null
        val address = doc.getString("address")
        val createdAt = doc.getLong("createdAt") ?: 0L
        val realId = doc.getString("id") ?: doc.id
        val routeId = doc.getString("routeId")
        return ClientDto(id = realId, name = name, address = address, createdAt = createdAt, routeId = routeId)
    }
}
