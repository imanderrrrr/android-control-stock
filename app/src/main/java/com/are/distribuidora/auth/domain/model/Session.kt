package com.are.distribuidora.auth.domain.model

/**
 * Sesión persistida localmente.
 *
 * Contrato:
 * - Si existe Session -> el usuario está logueado.
 * - authToken puede ser null o inválido (sesión degradada), pero NO implica logout.
 * - Logout solo por acción explícita.
 */
data class Session(
    val userId: String,
    val email: String,
    val authToken: String?,
    val lastLoginAt: Long,
)
