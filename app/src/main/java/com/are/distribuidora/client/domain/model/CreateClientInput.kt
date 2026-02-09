package com.are.distribuidora.client.domain.model

data class CreateClientInput(
    val name: String,
    val address: String?,
    val phone: String?,
    val latitude: Double?,
    val longitude: Double?,
    val maxOrderAmountInCents: Long?,
    val routeId: String
)
