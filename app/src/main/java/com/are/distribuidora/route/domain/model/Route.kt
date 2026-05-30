package com.are.distribuidora.route.domain.model

/**
 * Modelo de dominio para Ruta.
 *
 * Reglas de negocio:
 * - Una ruta agrupa muchos clientes.
 * - Un cliente solo puede pertenecer a una ruta (client.routeId).
 */
data class Route(
    val id: String,
    val name: String,
    /**
     * Día de entrega (modificable).
     * Se usa Int 1..7 para evitar problemas de localización/serialización.
     * 1 = Lunes ... 7 = Domingo.
     */
    val deliveryDay: Int,
    /** Cantidad de clientes en la ruta (derivada de Room). */
    val clientsCount: Int,
    /** True si ya se sincronizó a Firestore. */
    val synced: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft-delete: true = marcada para eliminación. */
    val isDeleted: Boolean = false,
)
