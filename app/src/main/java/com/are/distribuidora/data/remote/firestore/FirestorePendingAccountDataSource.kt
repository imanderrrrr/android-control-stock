package com.are.distribuidora.data.remote.firestore

import android.util.Log
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Data source de Firestore para cuentas pendientes.
 * Colección: `pending_accounts`
 */
class FirestorePendingAccountDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    companion object {
        private const val TAG = "PendingAccSync"
        private const val COLLECTION = "pending_accounts"
    }

    private val collection get() = firestore.collection(COLLECTION)

    /**
     * Sube o actualiza una cuenta pendiente en Firestore.
     */
    suspend fun upsert(entity: PendingAccountEntity) {
        val data = hashMapOf<String, Any?>(
            "id" to entity.id,
            "routeId" to entity.routeId,
            "clientId" to entity.clientId,
            "clientName" to entity.clientName,
            "routeName" to entity.routeName,
            "amountCents" to entity.amountCents,
            "invoiceRemoteUrl" to entity.invoiceRemoteUrl,
            "dueDateMillis" to entity.dueDateMillis,
            "isPaid" to entity.isPaid,
            "notes" to entity.notes,
            "createdAt" to entity.createdAt,
            "updatedAt" to entity.updatedAt,
            "resolvedBy" to entity.resolvedBy,
            "resolvedAt" to entity.resolvedAt,
            "resolvedAction" to entity.resolvedAction,
        )

        // Filtrar nulls para no sobrescribir campos existentes con null
        val filteredData = data.filterValues { it != null }

        collection.document(entity.id)
            .set(filteredData, SetOptions.merge())
            .await()

        Log.d(TAG, "Upserted to Firestore: ${entity.id} action=${entity.resolvedAction}")
    }

    /**
     * Descarga todas las cuentas pendientes de Firestore.
     */
    suspend fun fetchAll(): List<PendingAccountEntity> {
        val snapshot = collection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            try {
                PendingAccountEntity(
                    id = doc.getString("id") ?: doc.id,
                    routeId = doc.getString("routeId") ?: return@mapNotNull null,
                    clientId = doc.getString("clientId") ?: return@mapNotNull null,
                    clientName = doc.getString("clientName") ?: "",
                    routeName = doc.getString("routeName") ?: "",
                    amountCents = doc.getLong("amountCents") ?: 0L,
                    invoicePhotoUri = null, // Local-only field
                    invoiceRemoteUrl = doc.getString("invoiceRemoteUrl"),
                    dueDateMillis = doc.getLong("dueDateMillis") ?: 0L,
                    isPaid = doc.getBoolean("isPaid") ?: false,
                    notes = doc.getString("notes"),
                    createdAt = doc.getLong("createdAt") ?: 0L,
                    updatedAt = doc.getLong("updatedAt") ?: 0L,
                    syncStatus = SyncStatus.SYNCED,
                    resolvedBy = doc.getString("resolvedBy"),
                    resolvedAt = doc.getLong("resolvedAt"),
                    resolvedAction = doc.getString("resolvedAction"),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse doc ${doc.id}", e)
                null
            }
        }
    }

    /**
     * Elimina un documento de Firestore.
     */
    suspend fun delete(id: String) {
        collection.document(id).delete().await()
        Log.d(TAG, "Deleted from Firestore: $id")
    }
}
