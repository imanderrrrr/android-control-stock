package com.are.distribuidora.route.data.mapper

import com.are.distribuidora.route.data.local.entity.RouteEntity
import com.are.distribuidora.route.data.remote.dto.RouteDto
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.data.local.SyncStatus

internal fun RouteEntity.toDomain(clientsCount: Int): Route =
    Route(
        id = id,
        name = name,
        deliveryDay = deliveryDay,
        clientsCount = clientsCount,
        synced = (syncStatus == SyncStatus.SYNCED),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun Route.toEntity(synced: Boolean): RouteEntity =
    RouteEntity(
        id = id,
        name = name,
        deliveryDay = deliveryDay,
        syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun Route.toDto(synced: Boolean): RouteDto =
    RouteDto(
        id = id,
        name = name,
        deliveryDay = deliveryDay,
        synced = synced,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun RouteDto.toEntity(): RouteEntity =
    RouteEntity(
        id = id,
        name = name,
        deliveryDay = deliveryDay,
        syncStatus = if (synced) SyncStatus.SYNCED else SyncStatus.PENDING,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
