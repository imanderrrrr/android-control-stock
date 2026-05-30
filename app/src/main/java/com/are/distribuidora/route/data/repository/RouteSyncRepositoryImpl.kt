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

    companion object {
        /** Rutas soft-deleted hace más de 2 días se eliminan permanentemente. */
        private const val HARD_DELETE_GRACE_PERIOD_MS = 2L * 24 * 60 * 60 * 1000
    }

    override suspend fun syncPending(limit: Int) {
        val pending = local.getPendingRoutes(limit)
        if (pending.isEmpty()) return

        for (entity in pending) {
            if (entity.isDeleted) {
                // Ruta marcada para eliminación: sincronizar soft-delete a Firestore
                remote.softDeleteRoute(entity.id, entity.updatedAt)
                local.markSynced(routeId = entity.id, syncStatus = SyncStatus.SYNCED)
            } else {
                // Ruta normal: upsert a Firestore
                val route = com.are.distribuidora.route.domain.model.Route(
                    id = entity.id,
                    name = entity.name,
                    deliveryDay = entity.deliveryDay,
                    clientsCount = 0,
                    synced = (entity.syncStatus == SyncStatus.SYNCED),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isDeleted = false,
                )
                remote.upsertRoute(route.toDto(synced = true))
                local.markSynced(routeId = entity.id, syncStatus = SyncStatus.SYNCED)
            }
        }
    }

    override suspend fun pullRemote() {
        val remoteRoutes = remote.fetchRoutes()
        if (remoteRoutes.isEmpty()) return

        val now = System.currentTimeMillis()
        val cutoff = now - HARD_DELETE_GRACE_PERIOD_MS

        for (dto in remoteRoutes) {
            val entity = dto.toEntity()

            if (entity.isDeleted) {
                if (entity.updatedAt < cutoff) {
                    // Pasó el período de gracia → hard-delete local + Firestore
                    local.hardDeleteById(entity.id)
                    try { remote.deleteRoute(entity.id) } catch (_: Exception) { /* best-effort */ }
                } else {
                    // Dentro del período de gracia → guardar como soft-deleted para que la UI la oculte
                    val existing = local.getByIdIncludingDeleted(entity.id)
                    if (existing == null || entity.updatedAt >= existing.updatedAt) {
                        local.upsert(entity)
                    }
                }
            } else {
                // Ruta activa: upsert normal
                local.upsert(entity)
            }
        }

        // Purgar rutas locales soft-deleted que pasaron el período de gracia
        local.hardDeleteOldSoftDeleted(cutoff)
    }
}
