package com.are.distribuidora.auth.data.remote

/**
 * Datasource remoto responsable de login y cierre de sesión remota.
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

    /**
     * Cierra la sesión remota (FirebaseAuth.signOut). Sin esto, `FirebaseAuth.currentUser`
     * seguiría siendo el usuario anterior y el siguiente usuario del mismo teléfono
     * heredaría su uid (y con él su rol cacheado).
     */
    suspend fun signOut()
}
