package com.are.distribuidora.client.domain.repository

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.core.result.Result

interface ClientRepository {
    suspend fun create(client: Client): Result<Unit>
    suspend fun getClients(limit: Int = 50): Result<List<Client>>
    suspend fun getClientById(id: String): Result<Client?>

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
}
