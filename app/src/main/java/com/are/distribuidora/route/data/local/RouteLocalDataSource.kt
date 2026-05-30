package com.are.distribuidora.route.data.local

import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.route.data.local.dao.RouteDao
import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.are.distribuidora.data.local.SyncStatus

class RouteLocalDataSource(
    private val db: DistribuidoraDatabase,
    private val routeDao: RouteDao,
    private val clientDao: ClientDao,
) {
    @Deprecated(
        message = "Use upsert(...) instead",
        replaceWith = ReplaceWith("upsert(entity)")
    )
    suspend fun upsertRoute(entity: RouteEntity) = upsert(entity)

    suspend fun upsert(entity: RouteEntity) = routeDao.upsert(entity)

    suspend fun upsertAll(entities: List<RouteEntity>) {
        if (entities.isNotEmpty()) {
            val ids = entities.joinToString(separator = ",") { it.id }
        }
        routeDao.upsertAll(entities)
    }

    suspend fun getRoutes(limit: Int): List<RouteEntity> = routeDao.getAll(limit)

    suspend fun getRouteById(id: String): RouteEntity? = routeDao.getById(id)

    suspend fun getByIdIncludingDeleted(id: String): RouteEntity? = routeDao.getByIdIncludingDeleted(id)

    suspend fun updateDeliveryDay(routeId: String, deliveryDay: Int, updatedAt: Long) =
        routeDao.updateDeliveryDay(id = routeId, deliveryDay = deliveryDay, updatedAt = updatedAt)

    suspend fun markSynced(routeId: String, syncStatus: SyncStatus) = routeDao.markSynced(routeId, syncStatus)

    suspend fun getPendingRoutes(limit: Int): List<RouteEntity> = routeDao.getPendingSync(limit)

    suspend fun softDelete(routeId: String, now: Long) = routeDao.softDelete(routeId, now)

    suspend fun hardDeleteById(routeId: String) = routeDao.hardDeleteById(routeId)

    suspend fun hardDeleteOldSoftDeleted(cutoffMillis: Long) = routeDao.hardDeleteOldSoftDeleted(cutoffMillis)

    suspend fun countClientsByRoute(routeId: String): Int =
        clientDao.countByRoute(routeId)

    suspend fun assignClientToRoute(clientId: String, routeId: String) {
        db.runInTransaction {
            clientDao.updateRoute(clientId = clientId, routeId = routeId)
        }
    }

    // Reemplaza todas las rutas locales por la lista proporcionada (estrategia replaceAll).
    @Suppress("unused")
    suspend fun replaceAll(entities: List<RouteEntity>) {
        if (entities.isNotEmpty()) {
            val ids = entities.joinToString(separator = ",") { it.id }
        }

        db.runInTransaction {
            routeDao.deleteAll()
            if (entities.isNotEmpty()) {
                routeDao.upsertAll(entities)
            }
        }
    }

    suspend fun countRoutes(): Int = routeDao.countRoutes()
}
