package com.are.distribuidora.core.result

// Errores de negocio y técnicos, explícitos y asertables en tests.
sealed class Failure {

    data object NetworkError : Failure()
    data object DatabaseError : Failure()
    data object NotFound : Failure()

    // Error de validación de dominio
    data class ValidationError(val message: String) : Failure()

    /**
     * Intento de crear un pedido para un cliente que ya tiene uno activo el mismo día.
     * Regla: 1 pedido por cliente por día de creación.
     *
     * @param existingOrderId  ID del pedido ya existente (para navegación opcional en UI).
     */
    data class DuplicateOrder(val existingOrderId: String) : Failure()

    /**
     * El total del pedido excede el límite de compra configurado para el cliente.
     *
     * @param limitInCents  límite configurado en centavos.
     * @param totalInCents  total del pedido en centavos.
     */
    data class OrderLimitExceeded(
        val limitInCents: Long,
        val totalInCents: Long,
    ) : Failure()

    data object UnknownError : Failure()

    override fun toString(): String = when (this) {
        NetworkError -> "NetworkError"
        DatabaseError -> "DatabaseError"
        NotFound -> "NotFound"
        is ValidationError -> "ValidationError(message=${this.message})"
        is DuplicateOrder -> "DuplicateOrder(existingOrderId=${this.existingOrderId})"
        is OrderLimitExceeded -> "OrderLimitExceeded(limit=${this.limitInCents}, total=${this.totalInCents})"
        UnknownError -> "UnknownError"
    }
}
