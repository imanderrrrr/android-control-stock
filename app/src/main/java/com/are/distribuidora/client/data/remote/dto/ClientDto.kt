package com.are.distribuidora.client.data.remote.dto

data class ClientDto(
    val id: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val maxOrderAmountInCents: Long?,
    val isActive: Boolean,
    val isDeleted: Boolean = false,
    val auditCreatedBy: String,
    val auditLastModifiedBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    val routeId: String?,
)
