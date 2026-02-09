package com.are.distribuidora.core.result

// Errores de negocio y técnicos, explícitos y asertables en tests.
sealed class Failure {

    data object NetworkError : Failure()
    data object DatabaseError : Failure()
    data object NotFound : Failure()

    // Error de validación de dominio
    data class ValidationError(val message: String) : Failure()

    data object UnknownError : Failure()

    override fun toString(): String = when (this) {
        NetworkError -> "NetworkError"
        DatabaseError -> "DatabaseError"
        NotFound -> "NotFound"
        is ValidationError -> "ValidationError(message=${this.message})"
        UnknownError -> "UnknownError"
    }
}
