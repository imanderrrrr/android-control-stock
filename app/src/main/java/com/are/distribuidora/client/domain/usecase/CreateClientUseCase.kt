package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.domain.model.CreateClientInput
import com.are.distribuidora.client.domain.util.TextFormatter
import com.are.distribuidora.route.domain.repository.RouteRepository
import javax.inject.Inject

class CreateClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
    private val routeRepository: RouteRepository,
    private val authRepository: com.are.distribuidora.auth.domain.repository.AuthRepository,
) {

    suspend operator fun invoke(input: CreateClientInput) {

        require(input.name.isNotBlank()) {
            "Client name cannot be blank"
        }

        require(input.routeId.isNotBlank()) {
            "RouteId cannot be blank"
        }

        require(input.routeId != "__unassigned__") {
            "Invalid route"
        }

        if (input.maxOrderAmountInCents != null) {
            require(input.maxOrderAmountInCents >= 0) {
                "Max order amount must be non-negative"
            }
        }

        // Validar formato de teléfono si se proporciona
        if (input.phone != null && !TextFormatter.isValidPhoneNumber(input.phone)) {
            throw IllegalArgumentException("El número de teléfono debe tener exactamente 8 dígitos")
        }

        val routeExists = routeRepository.existsById(input.routeId)

        require(routeExists) {
            "Route does not exist"
        }

        val session = authRepository.getCurrentSession()
            ?: throw IllegalStateException("User must be logged in to create a client")

        val currentTime = System.currentTimeMillis()

        // Normalizar datos según reglas de negocio
        val normalizedName = TextFormatter.capitalizeWords(input.name.trim())
        val normalizedPhone = TextFormatter.formatPhoneNumber(input.phone)
        val normalizedAddress = input.address?.trim()?.let { TextFormatter.capitalizeWords(it) }

        val client = Client(
            id = java.util.UUID.randomUUID().toString(),
            name = normalizedName,
            phone = normalizedPhone,
            address = normalizedAddress,
            latitude = input.latitude,
            longitude = input.longitude,
            maxOrderAmountInCents = input.maxOrderAmountInCents,
            isActive = true,
            isDeleted = false,
            routeId = input.routeId,
            syncStatus = com.are.distribuidora.data.local.SyncStatus.PENDING,
            createdAt = currentTime,
            updatedAt = currentTime,
            createdBy = session.userId,
            lastModifiedBy = session.userId
        )

        clientRepository.create(client)
    }
}
