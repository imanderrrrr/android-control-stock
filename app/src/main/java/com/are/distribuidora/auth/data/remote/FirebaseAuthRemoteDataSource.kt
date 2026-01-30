package com.are.distribuidora.auth.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await

/**
 * Implementación real de [AuthRemoteDataSource] basada en Firebase Authentication.
 *
 * Mantiene el enfoque offline-first del repositorio:
 * - Este datasource SOLO intenta autenticación remota cuando el usuario ejecuta login.
 * - En errores NO borra la sesión local ni hace logout automático.
 *
 * Restricciones:
 * - NO usar listeners globales.
 * - NO refrescar tokens manualmente (se usa getIdToken(false)).
 */
class FirebaseAuthRemoteDataSource(
    private val firebaseAuth: FirebaseAuth,
) : AuthRemoteDataSource {

    override suspend fun login(email: String, password: String): AuthRemoteDataSource.LoginResponse {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val user = authResult.user
                ?: throw IllegalStateException("FirebaseAuth devolvió un usuario nulo.")

            val userEmail = user.email
                ?: throw IllegalStateException("El usuario autenticado no tiene email disponible.")

            // Importante: NO forzamos refresh (false) para evitar lógica extra.
            val tokenResult = user.getIdToken(false).await()

            AuthRemoteDataSource.LoginResponse(
                userId = user.uid,
                email = userEmail,
                authToken = tokenResult.token,
            )
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Password incorrecta o email mal formateado.
            throw Exception("Credenciales inválidas. Verifica email y contraseña.", e)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Cuenta no existe / deshabilitada.
            throw Exception("Usuario no encontrado o deshabilitado.", e)
        } catch (e: FirebaseNetworkException) {
            // Sin red, timeouts, etc.
            throw Exception("Error de red. Verifica tu conexión e inténtalo nuevamente.", e)
        } catch (e: Exception) {
            // Importante: NO hacemos logout ni tocamos sesión local.
            throw Exception(e.message ?: "Error inesperado al iniciar sesión.", e)
        }
    }
}
