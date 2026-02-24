package com.are.distribuidora.client.data.mapper

import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.client.data.remote.dto.ClientDto
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.toSyncState
import com.are.distribuidora.domain.core.SyncState

private const val PLACEHOLDER_ROUTE_ID = "__unassigned__"

/**
 * Mappers de capa data para Client.
 *
 * Regla: Client (dominio) es el modelo soberano.
 * El mapeo SyncStatus ↔ SyncState ocurre aquí, en la frontera data↔domain.
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
        syncStatus = syncState.toSyncStatus(),
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
        syncState = syncStatus.toSyncState(),
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
        syncStatus = SyncStatus.SYNCED, // From remote => SYNCED
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = auditCreatedBy,
        lastModifiedBy = auditLastModifiedBy
    )

/** Mapeo inverso SyncState → SyncStatus para persistir en Room. */
internal fun SyncState.toSyncStatus(): SyncStatus = when (this) {
    SyncState.SYNCED   -> SyncStatus.SYNCED
    SyncState.SYNCING  -> SyncStatus.SYNCING
    SyncState.CONFLICT -> SyncStatus.CONFLICT
    SyncState.FAILED   -> SyncStatus.FAILED
    SyncState.PENDING  -> SyncStatus.PENDING
}

