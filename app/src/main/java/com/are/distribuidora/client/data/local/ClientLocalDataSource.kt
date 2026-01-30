package com.are.distribuidora.client.data.local

import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.client.data.local.entity.ClientEntity

class ClientLocalDataSource(
    private val dao: ClientDao,
) {
    suspend fun upsert(entity: ClientEntity) = dao.upsert(entity)

    /**
     * Upsert en lote.
     * Importante: este método NO debe capturar excepciones.
     */
    suspend fun upsert(entities: List<ClientEntity>) = dao.upsertAll(entities)

    suspend fun getById(id: String): ClientEntity? = dao.getById(id)
    suspend fun getAll(limit: Int): List<ClientEntity> = dao.getAll(limit)
}
