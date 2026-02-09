package com.are.distribuidora.client.domain.repository

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.core.result.Result
import kotlinx.coroutines.flow.Flow

interface ClientRepository {
    suspend fun create(client: Client): Result<Unit>

    suspend fun insert(client: Client)
    suspend fun getClients(limit: Int = 50): Result<List<Client>>
    suspend fun getClientById(id: String): Result<Client?>
    suspend fun getByRouteId(routeId: String, limit: Int = 50): Result<List<Client>>
    fun observeByRouteId(routeId: String, limit: Int = 100): Flow<List<Client>>

    /**
     * Marca un cliente para eliminación (Soft Delete + Sync PENDING_DELETE).
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Sincronización descendente explícita (remoto -> local).
     *
     * Contrato de diseño (catálogo offline-first):
     * - Room es fuente de verdad.
     * - Firestore se usa solo como mecanismo de distribución.
     * - La operación es idempotente (segura de ejecutar múltiples veces).
     * - No borra datos locales.
     * - Si falla la red/Firestore: NO es fatal (debe retornar Success(Unit)).
     * - Si falla la persistencia en Room: retorna Error(DatabaseError).
     */
    suspend fun syncClients(limit: Int = 50): Result<Unit>

    /**
     * Actualiza un cliente existente.
     *
     * Reglas:
     * - Debe existir previamente (id válido).
     * - No permite editar clientes eliminados.
     * - Actualiza updatedAt y marca como PENDING_UPDATE.
     */
    suspend fun update(client: Client): Result<Unit>
}
