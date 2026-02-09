package com.are.distribuidora.client.data.remote

import android.util.Log
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

    private companion object {
        private const val TAG = "ClientRemoteDS"
    }

    override suspend fun uploadClient(client: ClientDto) {
        val payload = hashMapOf(
            "id" to client.id,
            "name" to client.name,
            "address" to client.address,
            "createdAt" to client.createdAt,
            "routeId" to client.routeId,
            "phone" to client.phone,
            "latitude" to client.latitude,
            "longitude" to client.longitude,
            "maxOrderAmountInCents" to client.maxOrderAmountInCents,
            "isActive" to client.isActive,
            "isDeleted" to client.isDeleted,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "auditCreatedBy" to client.auditCreatedBy,
            "auditLastModifiedBy" to client.auditLastModifiedBy,
        )
        collection.document(client.id).set(payload).await()
    }

    override suspend fun softDeleteClient(id: String) {
        val payload = mapOf(
            "isDeleted" to true,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        collection.document(id).update(payload).await()
    }

    override suspend fun fetchClients(limit: Int): List<ClientDto> {
        Log.d(TAG, "fetchClients(limit=$limit) -> starting")

        val primarySnap = try {
            collection
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "fetchClients primary query (orderBy updatedAt) failed", e)
            null
        }

        val docs = when {
            primarySnap == null -> {
                Log.w(TAG, "fetchClients primarySnap=null -> using fallback query")
                collection.limit(limit.toLong()).get().await().documents
            }

            primarySnap.documents.isNotEmpty() -> {
                Log.d(TAG, "fetchClients primary query returned ${primarySnap.size()} docs")
                primarySnap.documents
            }

            else -> {
                Log.w(TAG, "fetchClients primary query returned 0 docs -> using fallback query")
                collection.limit(limit.toLong()).get().await().documents
            }
        }

        Log.d(TAG, "fetchClients total docs to map = ${docs.size}")

        val mapped = docs.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name")
            if (name.isNullOrBlank()) {
                Log.w(TAG, "Skipping client docId=${doc.id} (id=$id) because name is null/blank")
                return@mapNotNull null
            }

            val address = doc.getString("address")
            val createdAt = doc.getLong("createdAt") ?: 0L
            val routeId = doc.getString("routeId")
            val phone = doc.getString("phone")
            val latitude = doc.getDouble("latitude")
            val longitude = doc.getDouble("longitude")
            val maxOrderAmountInCents = doc.getLong("maxOrderAmountInCents")
            val isActive = doc.getBoolean("isActive") ?: true
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            val auditCreatedBy = doc.getString("auditCreatedBy") ?: ""
            val auditLastModifiedBy = doc.getString("auditLastModifiedBy") ?: ""

            val updatedAtObj = doc.get("updatedAt")
            val updatedAt = when (updatedAtObj) {
                is Long -> updatedAtObj
                is com.google.firebase.Timestamp -> updatedAtObj.toDate().time
                else -> null
            } ?: createdAt

            if (routeId.isNullOrBlank()) {
                Log.w(TAG, "Client(id=$id) has blank routeId. It will likely be skipped later due to FK.")
            }

            ClientDto(
                id = id,
                name = name,
                phone = phone,
                address = address,
                latitude = latitude,
                longitude = longitude,
                maxOrderAmountInCents = maxOrderAmountInCents,
                isActive = isActive,
                isDeleted = isDeleted,
                auditCreatedBy = auditCreatedBy,
                auditLastModifiedBy = auditLastModifiedBy,
                createdAt = createdAt,
                updatedAt = updatedAt,
                routeId = routeId,
            )
        }

        Log.d(TAG, "fetchClients mapped=${mapped.size} (from docs=${docs.size})")
        return mapped
    }

    override suspend fun fetchClientsAfter(timestamp: Long): List<ClientDto> {
        Log.d(TAG, "fetchClientsAfter(timestamp=$timestamp) -> starting")

        val date = java.util.Date(timestamp)

        val snap = try {
            collection
                .whereGreaterThanOrEqualTo("updatedAt", date)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .orderBy(com.google.firebase.firestore.FieldPath.documentId())
                .get()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "fetchClientsAfter failed", e)
            throw e
        }

        if (snap.isEmpty) return emptyList()

        return snap.documents.mapNotNull { doc ->
            // Use same mapping logic as fetchClients
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name")
            if (name.isNullOrBlank()) return@mapNotNull null

            val address = doc.getString("address")
            val createdAt = doc.getLong("createdAt") ?: 0L
            val routeId = doc.getString("routeId")
            val phone = doc.getString("phone")
            val latitude = doc.getDouble("latitude")
            val longitude = doc.getDouble("longitude")
            val maxOrderAmountInCents = doc.getLong("maxOrderAmountInCents")
            val isActive = doc.getBoolean("isActive") ?: true
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            val auditCreatedBy = doc.getString("auditCreatedBy") ?: ""
            val auditLastModifiedBy = doc.getString("auditLastModifiedBy") ?: ""

            val updatedAtObj = doc.get("updatedAt")
            val updatedAt = when (updatedAtObj) {
                is Long -> updatedAtObj
                is com.google.firebase.Timestamp -> updatedAtObj.toDate().time
                else -> null
            } ?: createdAt

            ClientDto(
                id = id,
                name = name,
                phone = phone,
                address = address,
                latitude = latitude,
                longitude = longitude,
                maxOrderAmountInCents = maxOrderAmountInCents,
                isActive = isActive,
                isDeleted = isDeleted,
                auditCreatedBy = auditCreatedBy,
                auditLastModifiedBy = auditLastModifiedBy,
                createdAt = createdAt,
                updatedAt = updatedAt,
                routeId = routeId,
            )
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
        val phone = doc.getString("phone")
        val latitude = doc.getDouble("latitude")
        val longitude = doc.getDouble("longitude")
        val maxOrderAmountInCents = doc.getLong("maxOrderAmountInCents")
        val isActive = doc.getBoolean("isActive") ?: true
        val isDeleted = doc.getBoolean("isDeleted") ?: false
        val auditCreatedBy = doc.getString("auditCreatedBy") ?: ""
        val auditLastModifiedBy = doc.getString("auditLastModifiedBy") ?: ""
        val updatedAtObj = doc.get("updatedAt")
        val updatedAt = when (updatedAtObj) {
            is Long -> updatedAtObj
            is com.google.firebase.Timestamp -> updatedAtObj.toDate().time
            else -> null
        } ?: return null
        return ClientDto(
            id = realId,
            name = name,
            phone = phone,
            address = address,
            latitude = latitude,
            longitude = longitude,
            maxOrderAmountInCents = maxOrderAmountInCents,
            isActive = isActive,
            isDeleted = isDeleted,
            auditCreatedBy = auditCreatedBy,
            auditLastModifiedBy = auditLastModifiedBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            routeId = routeId,
        )
    }
}
