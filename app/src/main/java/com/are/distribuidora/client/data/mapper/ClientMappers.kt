package com.are.distribuidora.client.data.mapper

import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.domain.model.Client

private const val PLACEHOLDER_ROUTE_ID = "__unassigned__"

/**
 * Mappers de capa data para Client.
 *
 * Regla: Client (dominio) es el modelo soberano.
 */
internal fun Client.toEntity(): ClientEntity =
    ClientEntity(
        id = id,
        name = name,
        phone = phone,
        address = address,
        latitude = latitude,
        longitude = longitude,
        maxOrderAmountInCents = maxOrderAmountInCents,
        isActive = isActive,
        isDeleted = isDeleted,
        routeId = routeId,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        lastModifiedBy = lastModifiedBy
    )

internal fun ClientEntity.toDomain(): Client =
    Client(
        id = id,
        name = name,
        phone = phone,
        address = address,
        latitude = latitude,
        longitude = longitude,
        maxOrderAmountInCents = maxOrderAmountInCents,
        isActive = isActive,
        isDeleted = isDeleted,
        routeId = routeId,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        lastModifiedBy = lastModifiedBy
    )

internal fun Client.toDto(): ClientDto =
    ClientDto(
        id = id,
        name = name,
        phone = phone,
        address = address,
        latitude = latitude,
        longitude = longitude,
        maxOrderAmountInCents = maxOrderAmountInCents,
        isActive = isActive,
        isDeleted = isDeleted,
        auditCreatedBy = createdBy,
        auditLastModifiedBy = lastModifiedBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        routeId = routeId,
    )

internal fun ClientDto.toEntity(): ClientEntity =
    ClientEntity(
        id = id,
        name = name,
        phone = phone,
        address = address,
        latitude = latitude,
        longitude = longitude,
        maxOrderAmountInCents = maxOrderAmountInCents,
        isActive = isActive,
        isDeleted = isDeleted,
        routeId = routeId!!,
        syncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED, // From remote => SYNCED
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = auditCreatedBy,
        lastModifiedBy = auditLastModifiedBy
    )
