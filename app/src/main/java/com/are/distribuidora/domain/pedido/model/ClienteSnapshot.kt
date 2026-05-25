package com.are.distribuidora.domain.pedido.model

/**
 * Snapshot de los datos del cliente al momento de crear el pedido.
 * Garantiza que el pedido sea renderizable aunque el cliente se elimine o cambie.
 */
import java.io.Serializable

data class ClienteSnapshot(
    val nombre: String,
    val telefono: String?,
    val direccion: String?
) : Serializable
