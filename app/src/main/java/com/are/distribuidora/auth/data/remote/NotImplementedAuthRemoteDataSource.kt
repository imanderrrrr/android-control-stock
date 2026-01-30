package com.are.distribuidora.auth.data.remote

/**
 * Implementación placeholder.
 *
 * Fuera de alcance: integrar un backend real de autenticación.
 *
 * Importante:
 * - Existe para mantener el proyecto compilable.
 * - Cualquier llamada a login fallará con una excepción.
 *   El repositorio la traduce a Failure.NetworkError/UnknownError según corresponda.
 */
class NotImplementedAuthRemoteDataSource : AuthRemoteDataSource {
    override suspend fun login(email: String, password: String): AuthRemoteDataSource.LoginResponse {
        throw UnsupportedOperationException("Auth remoto no implementado en este proyecto")
    }
}
