package com.are.distribuidora.client.data.repository

import android.util.Log
import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.ClientRemoteMapper
import com.are.distribuidora.client.data.mapper.toDomain
import com.are.distribuidora.client.data.mapper.toDto
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.data.local.SyncStatus
import javax.inject.Inject
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementación DATA del contrato de dominio [ClientSyncRepository].
 *
 * Reglas:
 * - Puede usar DTOs/Room/Firestore/mapping.
 * - NO decide flujos (online/offline).
 * - NO retorna Result.
 * - NO sobrescribe cambios locales pendientes.
 */
class ClientSyncRepositoryImpl @Inject constructor(
    private val remote: ClientRemoteDataSource,
    private val local: ClientLocalDataSource,
    private val mapper: ClientRemoteMapper,
    private val database: com.are.distribuidora.data.local.DistribuidoraDatabase,
) : ClientSyncRepository {

    private val syncMutex = Mutex()

    private companion object {
        private const val TAG = "ClientSyncRepo"
    }

    override suspend fun fetchRemoteClients(): List<Client> = syncMutex.withLock {
        // Enfoque Delta Sync Seguro:
        val lastTimestamp = local.getMaxUpdatedAt() ?: 0L
        Log.d(TAG, "fetchRemoteClients: Delta Sync from timestamp=$lastTimestamp")

        // fetchClientsAfter incluye:
        // .whereGreaterThanOrEqualTo("updatedAt", Date(lastTimestamp))
        // .orderBy("updatedAt")
        // .orderBy(documentId) -> Determinismo
        val dtos = remote.fetchClientsAfter(lastTimestamp)
        
        Log.d(TAG, "fetchRemoteClients: remote returned delta dtos=${dtos.size}")
        dtos.map { dto -> mapper.toDomain(dto).copy(syncState = com.are.distribuidora.domain.core.SyncState.SYNCED) }
    }

    /**
     * Sincronización descendente (Remote -> Local)
     *
     * Reglas:
     * - Insertar si no existe.
     * - Nunca sobrescribir si local.syncStatus != SYNCED.
     * - Solo actualizar si remote.updatedAt > local.updatedAt.
     */
    override suspend fun saveLocalClients(clients: List<Client>) {
        // Enfoque Atomic Batch: Ejecutar todo dentro de una transacción.
        database.withTransaction {
            Log.d(TAG, "saveLocalClients: start atomic batch clients=${clients.size}")

            var inserted = 0
            var updated = 0
            var skippedOld = 0
            var deleted = 0
            var protected = 0

            clients.forEach { remoteClient ->
                // Regla FK Fail Fast Absoluto:
                // Si routeId está vacío, es una violación de integridad.
                if (remoteClient.routeId.isBlank()) {
                    Log.e(TAG, "CRITICAL: Client(id=${remoteClient.id}) has blank routeId. Aborting batch to prevent inconsistent state.")
                    throw java.lang.IllegalStateException("CRITICAL FK Violation: Client ${remoteClient.id} has no routeId")
                }

                // GUARD anti pérdida de datos (mismo criterio que productos):
                // Una fila local solo puede ser BORRADA o SOBRESCRITA por el remoto si es nueva
                // (no existe local) o está durablemente SYNCED. Cualquier estado sucio
                // (PENDING_CREATE/PENDING_UPDATE/PENDING_DELETE/SYNCING/CONFLICT) lleva un cambio
                // local sin subir y DEBE sobrevivir el downsync, sin importar timestamps;
                // uploadPendingClients() lo subirá y converge por LWW. Esto implementa por fin la
                // regla que la doc de la clase ya prometía: "Nunca sobrescribir si syncStatus != SYNCED".
                val localEntity = local.getById(remoteClient.id)
                if (localEntity != null && localEntity.syncStatus != SyncStatus.SYNCED) {
                    Log.d(TAG, "saveLocalClients: PROTECT local ${remoteClient.id} (syncStatus=${localEntity.syncStatus}); skip remote overwrite/delete")
                    protected++
                    return@forEach
                }

                // Soft delete remoto: solo aplica sobre filas limpias o inexistentes (las sucias
                // ya se protegieron arriba), evitando borrar un cliente con cambios sin subir.
                if (remoteClient.isDeleted) {
                    local.deleteInsideTransaction(remoteClient.id)
                    deleted++
                    return@forEach
                }

                val entity = mapper.toEntity(remoteClient).copy(
                    lastSyncedAt = System.currentTimeMillis()
                )

                if (localEntity == null) {
                    // Insertar nuevo
                    try {
                        local.insert(entity)
                        inserted++
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        Log.e(TAG, "CRITICAL: FK Violation inserting client ${entity.id}. Route ${entity.routeId} missing locally?", e)
                        throw e // Abort transaction
                    }
                } else {
                    // Regla Formal de Overwrite (Idempotencia):
                    // if (remote.updatedAt >= local.updatedAt) upsert(remote)
                    // NOTA: remoteClient ya tiene el updatedAt correcto (serverTimestamp) desde el mapeo.
                    if (entity.updatedAt >= localEntity.updatedAt) {
                        try {
                            local.update(entity)
                            updated++
                        } catch (e: android.database.sqlite.SQLiteConstraintException) {
                            Log.e(TAG, "CRITICAL: FK Violation updating client ${entity.id}.", e)
                            throw e // Abort transaction
                        }
                    } else {
                         skippedOld++
                    }
                }
            }
             Log.d(TAG, "saveLocalClients: Batch commited. Inserted=$inserted Updated=$updated Deleted=$deleted SkippedOld=$skippedOld Protected=$protected")
        }
    }

    /**
     * Sincronización ascendente (Local -> Remote)
     *
     * Reglas:
     * - Solo subir entidades con syncStatus != SYNCED.
     * - Si upload exitoso -> marcar como SYNCED.
     * - No usar upsert.
     */
    override suspend fun uploadPendingClients() = syncMutex.withLock {

        val pendingEntities = local.getPending(limit = 100)

        Log.d(TAG, "uploadPendingClients: Found ${pendingEntities.size} pending clients")

        pendingEntities.forEach { entity ->
            Log.d(TAG, "uploadPendingClients: Processing client id=${entity.id} status=${entity.syncStatus} isDeleted=${entity.isDeleted}")

            val originalStatus = entity.syncStatus

            if (originalStatus == SyncStatus.SYNCED) {
                Log.d(TAG, "uploadPendingClients: Skipping client ${entity.id} - already SYNCED")
                return@forEach
            }

            if (originalStatus == SyncStatus.PENDING_DELETE || entity.isDeleted) {
                Log.d(TAG, "uploadPendingClients: Processing SOFT DELETE for client ${entity.id}")
                local.markSyncing(entity.id)

                try {
                    remote.softDeleteClient(entity.id)
                    Log.d(TAG, "uploadPendingClients: Soft delete successful for client ${entity.id}")

                    val remoteSnapshot = try {
                        remote.getClientById(entity.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "uploadPendingClients: Failed to get remote snapshot for ${entity.id}", e)
                        null
                    }

                    if (remoteSnapshot != null) {
                        Log.d(TAG, "uploadPendingClients: Remote snapshot found, marking as SYNCED")
                        local.markSynced(
                            id = entity.id,
                            updatedAt = remoteSnapshot.updatedAt,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    } else {
                        Log.w(TAG, "uploadPendingClients: Remote snapshot NOT found, marking as FAILED")
                        local.markFailed(entity.id)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "uploadPendingClients: Soft delete FAILED for client ${entity.id}", e)
                    local.markFailed(entity.id)
                }

                return@forEach
            }

            Log.d(TAG, "uploadPendingClients: Processing UPLOAD for client ${entity.id}")
            local.markSyncing(entity.id)

            try {
                val dto = entity.toDomain().toDto()
                remote.uploadClient(dto)
                Log.d(TAG, "uploadPendingClients: Upload successful for client ${entity.id}")

                val remoteSnapshot = try {
                    remote.getClientById(dto.id)
                } catch (e: Exception) {
                    Log.e(TAG, "uploadPendingClients: Failed to get remote snapshot for ${dto.id}", e)
                    null
                }

                if (remoteSnapshot != null) {
                    Log.d(TAG, "uploadPendingClients: Remote snapshot found, marking as SYNCED")
                    local.markSynced(
                        id = entity.id,
                        updatedAt = remoteSnapshot.updatedAt,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                } else {
                    // Enfoque C (P0): snapshot aún no listo (serverTimestamp / consistencia eventual).
                    // No es un fallo definitivo. Volvemos a un estado reintentable para confirmar en el siguiente ciclo.
                    Log.w(TAG, "uploadPendingClients: Remote snapshot NOT found (eventual consistency), marking as PENDING_CREATE")
                    local.markPending(entity.id, pendingStatus = SyncStatus.PENDING_CREATE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "uploadPendingClients: Upload FAILED for client ${entity.id}", e)
                local.markFailed(entity.id)
            }
        }

        Log.d(TAG, "uploadPendingClients: Finished processing all pending clients")
        Unit
    }

    /**
     * Persistencia directa desde DTO remoto.
     */
    suspend fun saveLocalClientsFromRemote(
        dtos: List<com.are.distribuidora.client.data.remote.dto.ClientDto>
    ) {
        saveLocalClients(dtos.map { mapper.toDomain(it) })
    }
}
