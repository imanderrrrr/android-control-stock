package com.are.distribuidora.auth.data.remote

/**
 * Datasource remoto responsable SOLO de login.
 *
 * Nota: el backend remoto (REST/Firebase/Auth personalizado) no está definido en este repo.
 * Este contrato permite implementar la parte remota sin cambiar el resto del sistema.
 */
interface AuthRemoteDataSource {

    data class LoginResponse(
        val userId: String,
        val email: String,
        val authToken: String?,
    )

    suspend fun login(email: String, password: String): LoginResponse
}
