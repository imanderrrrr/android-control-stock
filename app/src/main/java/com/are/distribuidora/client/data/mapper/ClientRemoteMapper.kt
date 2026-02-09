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
            phone = dto.phone,
            address = dto.address,
            latitude = dto.latitude,
            longitude = dto.longitude,
            maxOrderAmountInCents = dto.maxOrderAmountInCents,
            isActive = dto.isActive,
            isDeleted = dto.isDeleted,
            routeId = dto.routeId.orEmpty(),
            syncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            createdBy = dto.auditCreatedBy,
            lastModifiedBy = dto.auditLastModifiedBy
        )

    fun toEntity(domain: Client): ClientEntity =
        ClientEntity(
            id = domain.id,
            name = domain.name,
            phone = domain.phone,
            address = domain.address,
            latitude = domain.latitude,
            longitude = domain.longitude,
            maxOrderAmountInCents = domain.maxOrderAmountInCents,
            isActive = domain.isActive,
            isDeleted = domain.isDeleted,
            routeId = domain.routeId,
            syncStatus = domain.syncStatus,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            createdBy = domain.createdBy,
            lastModifiedBy = domain.lastModifiedBy
        )

    fun toEntity(dto: ClientDto): ClientEntity =
        ClientEntity(
            id = dto.id,
            name = dto.name,
            phone = dto.phone,
            address = dto.address,
            latitude = dto.latitude,
            longitude = dto.longitude,
            maxOrderAmountInCents = dto.maxOrderAmountInCents,
            isActive = dto.isActive,
            isDeleted = dto.isDeleted,
            routeId = dto.routeId.orEmpty(),
            syncStatus = com.are.distribuidora.data.local.SyncStatus.SYNCED,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            createdBy = dto.auditCreatedBy,
            lastModifiedBy = dto.auditLastModifiedBy
        )

    fun toDto(domain: Client): ClientDto =
        ClientDto(
            id = domain.id,
            name = domain.name,
            phone = domain.phone,
            address = domain.address,
            latitude = domain.latitude,
            longitude = domain.longitude,
            maxOrderAmountInCents = domain.maxOrderAmountInCents,
            isActive = domain.isActive,
            isDeleted = domain.isDeleted,
            auditCreatedBy = domain.createdBy,
            auditLastModifiedBy = domain.lastModifiedBy,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            routeId = domain.routeId
        )
}
