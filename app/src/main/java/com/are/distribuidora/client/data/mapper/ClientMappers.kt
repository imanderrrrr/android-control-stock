package com.are.distribuidora.client.data.mapper

import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.domain.model.Client

/**
 * Mappers de capa data para Client.
 *
 * Regla: Client (dominio) es el modelo soberano.
 */
internal fun Client.toEntity(routeId: String? = null): ClientEntity =
    ClientEntity(
        id = id,
        name = name,
        address = address,
        createdAt = createdAt,
        routeId = routeId ?: this.routeId,
    )

internal fun ClientEntity.toDomain(): Client =
    Client(
        id = id,
        name = name,
        address = address,
        createdAt = createdAt,
        routeId = routeId,
    )

internal fun Client.toDto(routeId: String? = null): ClientDto =
    ClientDto(
        id = id,
        name = name,
        address = address,
        createdAt = createdAt,
        routeId = routeId ?: this.routeId,
    )

internal fun ClientDto.toEntity(): ClientEntity =
    ClientEntity(
        id = id,
        name = name,
        address = address,
        createdAt = createdAt,
        routeId = routeId,
    )
