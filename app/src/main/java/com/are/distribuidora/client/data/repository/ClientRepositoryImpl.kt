package com.are.distribuidora.client.data.repository

import com.are.distribuidora.client.data.local.ClientLocalDataSource
import com.are.distribuidora.client.data.mapper.toDomain
import com.are.distribuidora.client.data.mapper.toDto
import com.are.distribuidora.client.data.mapper.toEntity
import com.are.distribuidora.client.data.remote.ClientRemoteDataSource
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.repository.ClientSyncRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
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
class ClientRepositoryImpl @Inject constructor(
    private val local: ClientLocalDataSource,
    private val remote: ClientRemoteDataSource,
    private val sync: ClientSyncRepository,
) : ClientRepository {

    override suspend fun create(client: Client): Result<Unit> {
        // Mantener comportamiento actual: éxito depende SOLO del guardado local.
        return try {
            local.upsert(client.toEntity())
            try {
                // Dominio todavía no expone routeId; en creación se envía null.
                remote.uploadClient(client.toDto(routeId = client.routeId))
            } catch (_: Exception) {
                // best-effort
            }
            Result.Success(Unit)
        } catch (_: Exception) {
            Result.Error(Failure.DatabaseError)
        }
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
}
