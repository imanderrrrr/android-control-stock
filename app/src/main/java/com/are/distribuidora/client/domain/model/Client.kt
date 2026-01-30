package com.are.distribuidora.client.domain.model

/**
 * Modelo de dominio de cliente.
 * NOTA: No contiene datos sensibles.
 */
data class Client(
    val id: String,
    val name: String,
    val address: String?,
    val createdAt: Long,
    val routeId: String? = null,
)
