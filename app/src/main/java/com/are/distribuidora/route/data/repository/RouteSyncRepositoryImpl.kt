package com.are.distribuidora.route.data.repository

import com.are.distribuidora.route.data.local.RouteLocalDataSource
import com.are.distribuidora.route.data.mapper.toDto
import com.are.distribuidora.route.data.mapper.toEntity
import com.are.distribuidora.route.data.remote.RouteRemoteDataSource
import com.are.distribuidora.route.domain.repository.RouteSyncRepository
import com.are.distribuidora.data.local.SyncStatus
import javax.inject.Inject

class RouteSyncRepositoryImpl @Inject constructor(
    private val local: RouteLocalDataSource,
    private val remote: RouteRemoteDataSource,
) : RouteSyncRepository {

    override suspend fun syncPending(limit: Int) {
        val pending = local.getPendingRoutes(limit)
        if (pending.isEmpty()) return

        pending.forEach { entity ->
            // Intento de subida; si falla, dejamos pending para el siguiente intento.
            remote.upsertRoute(
                com.are.distribuidora.route.domain.model.Route(
                    id = entity.id,
                    name = entity.name,
                    deliveryDay = entity.deliveryDay,
                    clientsCount = 0,
                    synced = (entity.syncStatus == SyncStatus.SYNCED),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                ).toDto(synced = true)
            )
            local.markSynced(routeId = entity.id, syncStatus = SyncStatus.SYNCED)
        }
    }

    override suspend fun pullRemote() {
        val remoteRoutes = remote.fetchRoutes()

        if (remoteRoutes.isEmpty()) {
            // No modificar datos locales
            return
        }

        val entities = remoteRoutes.map { it.toEntity() }

        local.upsertAll(entities)
    }
}
