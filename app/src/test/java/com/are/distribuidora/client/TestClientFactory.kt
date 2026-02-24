package com.are.distribuidora.client

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.domain.core.SyncState

fun testClient(
    id: String = "client-1",
    name: String = "Test Client",
): Client {
    val now = System.currentTimeMillis()
    return Client(
        id = id,
        name = name,
        phone = null,
        address = null,
        latitude = null,
        longitude = null,
        maxOrderAmountInCents = null,
        isActive = true,
        isDeleted = false,
        routeId = "route-1",
        syncState = SyncState.PENDING,
        createdAt = now,
        updatedAt = now,
        createdBy = "test",
        lastModifiedBy = "test",
    )
}
