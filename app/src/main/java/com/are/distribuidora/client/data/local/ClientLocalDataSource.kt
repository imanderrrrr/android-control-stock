package com.are.distribuidora.client.data.local

import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.client.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

class ClientLocalDataSource(
    private val dao: ClientDao,
) {
    suspend fun insert(entity: ClientEntity): Long = dao.insert(entity)
    suspend fun update(entity: ClientEntity) = dao.update(entity)

    /**
     * Inserta múltiples clientes (ignorando conflictos).
     */
    suspend fun insertAll(entities: List<ClientEntity>) {
        entities.forEach { dao.insert(it) }
    }

    suspend fun getById(id: String): ClientEntity? = dao.getById(id)
    suspend fun getAll(limit: Int): List<ClientEntity> = dao.getAll(limit)

    suspend fun getPending(limit: Int): List<ClientEntity> =
        dao.getPending(
            statuses = listOf(
                com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE,
                com.are.distribuidora.data.local.SyncStatus.PENDING_UPDATE,
                com.are.distribuidora.data.local.SyncStatus.PENDING_DELETE,
                com.are.distribuidora.data.local.SyncStatus.FAILED
            ),
            limit = limit
        )

    suspend fun markAsPendingDelete(id: String) = dao.markAsPendingDelete(id)

    suspend fun markSyncing(id: String) = dao.markSyncing(id)

    suspend fun markFailed(id: String) = dao.markFailed(id)

    suspend fun markSynced(id: String, updatedAt: Long, lastSyncedAt: Long) =
        dao.markSynced(id = id, updatedAt = updatedAt, lastSyncedAt = lastSyncedAt)

    suspend fun deleteInsideTransaction(id: String) = dao.deleteInsideTransaction(id)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun getByRouteId(routeId: String, limit: Int): List<ClientEntity> =
        dao.getByRouteId(routeId = routeId, limit = limit)

    fun observeByRouteId(routeId: String, limit: Int): Flow<List<ClientEntity>> =
        dao.observeByRouteId(routeId = routeId, limit = limit)

    suspend fun markPending(id: String, pendingStatus: com.are.distribuidora.data.local.SyncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE) =
        dao.markPending(id = id, pendingStatus = pendingStatus)

    suspend fun getMaxUpdatedAt(): Long? = dao.getMaxUpdatedAt()

    suspend fun searchClients(query: String, limit: Int): List<ClientEntity> =
        dao.searchClients(query = query, limit = limit)

    suspend fun searchClientsByRoute(routeId: String, query: String): List<ClientEntity> =
        dao.searchClientsByRoute(routeId, query)

    suspend fun getInitialClientsByRoute(routeId: String): List<ClientEntity> =
        dao.getInitialClientsByRoute(routeId)
}
