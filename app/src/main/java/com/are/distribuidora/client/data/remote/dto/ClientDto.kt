package com.are.distribuidora.client.data.remote.dto

data class ClientDto(
    val id: String,
    val name: String,
    val address: String?,
    val createdAt: Long,
    val routeId: String?,
)
