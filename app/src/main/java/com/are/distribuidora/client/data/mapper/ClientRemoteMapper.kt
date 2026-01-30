package com.are.distribuidora.client.data.mapper

import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.domain.model.Client
import javax.inject.Inject

/**
 * Mapper técnico para el flujo de sync (remoto <-> dominio <-> local).
 *
 * Nota: el dominio NO debe conocer DTOs/Entities.
 */
class ClientRemoteMapper @Inject constructor() {

    fun toDomain(dto: ClientDto): Client =
        Client(
            id = dto.id,
            name = dto.name,
            address = dto.address,
            createdAt = dto.createdAt,
            routeId = dto.routeId,
        )

    fun toEntity(domain: Client, routeId: String? = null): ClientEntity =
        ClientEntity(
            id = domain.id,
            name = domain.name,
            address = domain.address,
            createdAt = domain.createdAt,
            routeId = routeId ?: domain.routeId,
        )

    fun toEntity(dto: ClientDto): ClientEntity =
        ClientEntity(
            id = dto.id,
            name = dto.name,
            address = dto.address,
            createdAt = dto.createdAt,
            routeId = dto.routeId,
        )
}
