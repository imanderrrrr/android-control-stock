package com.are.distribuidora.client.data.repository

import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.toDomain
import com.are.distribuidora.client.data.mapper.toDto
import com.are.distribuidora.client.data.mapper.toEntity
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementación "offline-first" para el catálogo de Clientes.
 *
 * Nota: este repositorio mantiene el comportamiento existente (create local->remoto best-effort,
 * lecturas desde Room) para NO romper features.
 *
 * La sincronización descendente (remoto->local) ya no decide política de red/errores aquí;
 * por compatibilidad del contrato legacy [ClientRepository.syncClients] delega en
 * [ClientSyncRepository] sin lógica adicional.
 */
import com.are.distribuidora.client.domain.validator.ClientValidator

class ClientRepositoryImpl @Inject constructor(
    private val local: ClientLocalDataSource,
    private val remote: ClientRemoteDataSource,
    private val sync: ClientSyncRepository,
    private val validator: ClientValidator, // New dependency
    private val coordinator: ClientSyncCoordinator,
) : ClientRepository {

    override suspend fun create(client: Client): Result<Unit> {
        // 1. Dominio: Validación estricta antes de tocar persistencia
        val validation = validator.validate(client)
        if (validation is Result.Error) {
            return validation
        }

        return try {
            // Regla: CREATE = Insertar localmente con estado PENDING_CREATE.
            // No hay subida inmediata. El SyncRepository se encarga después.
            val entity = client.toEntity().copy(
                syncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING_CREATE
            )
            local.insert(entity)
            coordinator.notifyLocalChange()
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun update(client: Client): Result<Unit> {
        return try {
            // 1. Obtener entidad existente para validar existencia y obtener datos previos
            val existingEntity = local.getById(client.id)
                ?: return Result.Error(Failure.ValidationError("Client not found"))

            // 2. Validar que no esté eliminado
            if (existingEntity.isDeleted) {
                return Result.Error(Failure.ValidationError("Cannot update deleted client"))
            }

            // Validar que no esté pendiente de eliminación (SyncStatus)
            if (existingEntity.syncStatus == com.are.distribuidora.data.local.SyncStatus.PENDING_DELETE) {
                return Result.Error(Failure.ValidationError("Cannot update client pending deletion"))
            }

            // 3. Optimización: Detectar si realmente hubo cambios
            val newEntityTemplate = client.toEntity()
            val hasChanges = existingEntity.name != newEntityTemplate.name ||
                    existingEntity.phone != newEntityTemplate.phone ||
                    existingEntity.address != newEntityTemplate.address ||
                    existingEntity.latitude != newEntityTemplate.latitude ||
                    existingEntity.longitude != newEntityTemplate.longitude ||
                    existingEntity.maxOrderAmountInCents != newEntityTemplate.maxOrderAmountInCents ||
                    existingEntity.isActive != newEntityTemplate.isActive ||
                    existingEntity.routeId != newEntityTemplate.routeId

            if (!hasChanges) {
                return Result.Success(Unit)
            }

            // 4. Preparar entidad para actualización:
            // - Mantener IDs y metadatos de creación
            // - Actualizar timestamp (Updated At)
            // - Marcar como PENDING_UPDATE
            // - Preservar lastSyncedAt del original (no lo reseteamos hasta que sincronice)
            val entityToUpdate = newEntityTemplate.copy(
                syncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING_UPDATE,
                updatedAt = System.currentTimeMillis(),
                lastSyncedAt = existingEntity.lastSyncedAt, // Preservar
                createdAt = existingEntity.createdAt,       // Preservar
                createdBy = existingEntity.createdBy        // Preservar
            )

            // 5. Actualizar en Room (Atomicidad garantizada por DAO @Update en single row)
            local.update(entityToUpdate)

            // 6. Notificar cambio local
            coordinator.notifyLocalChange()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun insert(client: Client) {
        local.insert(client.toEntity())
    }

    override suspend fun getClients(limit: Int): Result<List<Client>> {
        return try {
            val entities = local.getAll(limit = limit)
            Result.Success(entities.map { it.toDomain() })
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getClientById(id: String): Result<Client?> {
        return try {
            val entity = local.getById(id)
            Result.Success(entity?.toDomain())
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        return try {
            local.markAsPendingDelete(id)
            coordinator.notifyLocalChange()
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun syncClients(limit: Int): Result<Unit> {
        // API legacy: no decide nada, solo ejecuta remoto->local.
        return try {
            val clients = sync.fetchRemoteClients()
            sync.saveLocalClients(clients)
            Result.Success(Unit)
        } catch (_: Exception) {
            // Mantener semántica previa (DB fatal, red no fatal) no es posible distinguir aquí
            // sin lógica de negocio. Aun así, este método queda legacy; el flujo correcto es
            // SyncClientsUseCase.
            Result.Error(Failure.UnknownError)
        }
    }

    override suspend fun getByRouteId(routeId: String, limit: Int): Result<List<Client>> {
        return try {
            val entities = local.getByRouteId(routeId = routeId, limit = limit)
            Result.Success(entities.map { it.toDomain() })
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override fun observeByRouteId(routeId: String, limit: Int): Flow<List<Client>> {
        return local.observeByRouteId(routeId = routeId, limit = limit)
            .map { entities -> entities.map { it.toDomain() } }
    }
}
