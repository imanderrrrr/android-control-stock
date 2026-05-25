package com.are.distribuidora.route.data.repository

import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.data.local.RouteLocalDataSource
import com.are.distribuidora.route.data.mapper.toDto
import com.are.distribuidora.route.data.mapper.toEntity
import com.are.distribuidora.route.data.mapper.toDomain
import com.are.distribuidora.route.data.remote.RouteRemoteDataSource
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.repository.RouteRepository
import javax.inject.Inject

/**
 * Repositorio offline-first para Rutas:
 * - Room es fuente de verdad.
 * - Escrituras: primero local, luego remoto best-effort.
 */
class OfflineFirstRouteRepository @Inject constructor(
    private val local: RouteLocalDataSource,
    private val remote: RouteRemoteDataSource,
) : RouteRepository {

    override suspend fun create(route: Route): Result<Unit> {
        if (route.id.isBlank()) return Result.Error(Failure.ValidationError("id requerido"))
        if (route.name.isBlank()) return Result.Error(Failure.ValidationError("nombre requerido"))
        if (route.deliveryDay !in 1..7) return Result.Error(Failure.ValidationError("deliveryDay inválido"))

        // Guardar SIEMPRE local. synced depende de si el remoto tuvo éxito.
        return try {
            // 1) Persistimos pendiente por defecto
            local.upsertRoute(route.toEntity(synced = false))

            // 2) Best-effort remoto: si funciona, marcamos synced=true local.
            try {
                remote.upsertRoute(route.toDto(synced = true))
                local.markSynced(routeId = route.id, syncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED)
            } catch (_: Exception) {
                // mantener synced=false
            }
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getRoutes(limit: Int): Result<List<Route>> {
        return try {
            val entities = local.getRoutes(limit)
            val routes = entities.map { e ->
                val count = local.countClientsByRoute(e.id)
                e.toDomain(clientsCount = count)
            }
            Result.Success(routes)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getRouteById(id: String): Result<Route?> {
        return try {
            val entity = local.getRouteById(id)
            val route = entity?.let { e ->
                val count = local.countClientsByRoute(e.id)
                e.toDomain(clientsCount = count)
            }
            Result.Success(route)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun updateDeliveryDay(routeId: String, deliveryDay: Int): Result<Unit> {
        if (routeId.isBlank()) return Result.Error(Failure.ValidationError("routeId requerido"))
        if (deliveryDay !in 1..7) return Result.Error(Failure.ValidationError("deliveryDay inválido"))

        val now = System.currentTimeMillis()
        return try {
            local.updateDeliveryDay(routeId = routeId, deliveryDay = deliveryDay, updatedAt = now)
            // Cambio local => puede requerir re-sync si falla remoto
            local.markSynced(routeId = routeId, syncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING)

            try {
                remote.updateRouteDeliveryDay(routeId = routeId, deliveryDay = deliveryDay, updatedAt = now)
                local.markSynced(routeId = routeId, syncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED)
            } catch (_: Exception) {
                // mantener synced=false
            }

            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun assignClientToRoute(clientId: String, routeId: String): Result<Unit> {
        if (clientId.isBlank()) return Result.Error(Failure.ValidationError("clientId requerido"))
        if (routeId.isBlank()) return Result.Error(Failure.ValidationError("routeId inválido"))

        return try {
            local.assignClientToRoute(clientId = clientId, routeId = routeId)
            try {
                remote.assignClientRoute(clientId = clientId, routeId = routeId)
            } catch (_: Exception) {
                // best-effort
            }
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun existsById(routeId: String): Boolean {
        // Consultar directamente Room
        return local.getRouteById(routeId) != null
    }

    override suspend fun countRoutes(): Int = local.countRoutes()
}
