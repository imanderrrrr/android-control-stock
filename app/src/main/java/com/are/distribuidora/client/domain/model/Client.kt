package com.are.distribuidora.client.domain.model

/**
 * Modelo de dominio de cliente.
 * NOTA: No contiene datos sensibles.
 */
data class Client(
    val id: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val maxOrderAmountInCents: Long?,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val routeId: String,
    val syncStatus: com.are.distribuidora.data.local.SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String,
    val lastModifiedBy: String,
)
