package com.are.distribuidora.domain.pedido.model

/**
 * Representa la selección de cliente en el flujo de creación.
 */
import java.io.Serializable

sealed interface ClienteSelection : Serializable {
    data class Existente(val clienteId: String) : ClienteSelection
    data class Temporal(val snapshot: ClienteSnapshot) : ClienteSelection
}
