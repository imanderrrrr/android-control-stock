package com.are.distribuidora.auth.data.repository

import android.util.Log
import com.are.distribuidora.auth.data.local.AuthLocalDataSource
import com.are.distribuidora.auth.data.remote.AuthRemoteDataSource
import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository

/**
 * Repositorio de auth con reglas offline-first:
 * - La sesión se determina exclusivamente por la existencia de Session local.
 * - Login guarda Session local cuando el remoto responde OK.
 * - getCurrentSession / isSessionActive nunca llaman remoto.
 * - No hay logout automático.
 * - logout() cierra también la sesión de Firebase y borra la cache de rol/acceso,
 *   para que el siguiente usuario del mismo teléfono no herede el rol del anterior.
 */
class OfflineFirstAuthRepository(
    private val local: AuthLocalDataSource,
    private val remote: AuthRemoteDataSource,
    private val screenAccessRepository: ScreenAccessRepository? = null,
) : AuthRepository {

    private val tag = "Auth"

    override suspend fun login(email: String, password: String): Result<Session> {
        if (email.isBlank()) return Result.Error(Failure.ValidationError("email requerido"))
        if (password.isBlank()) return Result.Error(Failure.ValidationError("password requerido"))

        val response = try {
            remote.login(email = email, password = password)
        } catch (e: Exception) {
            Log.w(tag, "login: remoto falló (${e.message})")
            return Result.Error(Failure.NetworkError)
        }

        val session = Session(
            userId = response.userId,
            email = response.email,
            authToken = response.authToken,
            lastLoginAt = System.currentTimeMillis(),
        )

        return try {
            local.saveSession(session)
            Result.Success(session)
        } catch (e: Exception) {
            Log.e(tag, "login: persistencia local falló (${e.message})", e)
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun logout(): Result<Unit> {
        val uid = try { local.getSession()?.userId } catch (_: Exception) { null }

        // 1) Cache de rol/acceso del usuario que se va (best-effort).
        try {
            screenAccessRepository?.clearCache(uid)
        } catch (e: Exception) {
            Log.w(tag, "logout: clearCache falló (${e.message})")
        }
        // 2) Sesión de Firebase (best-effort: sin red también funciona, es local).
        try {
            remote.signOut()
        } catch (e: Exception) {
            Log.w(tag, "logout: signOut remoto falló (${e.message})")
        }
        // 3) Sesión local: es la que decide si hay sesión activa.
        return try {
            local.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "logout: clearSession falló (${e.message})", e)
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getCurrentSession(): Session? {
        return try {
            local.getSession()
        } catch (e: Exception) {
            // Por contrato, aquí no propagamos fallo fatal: si no podemos leer, no hay sesión usable.
            Log.e(tag, "getCurrentSession: lectura local falló (${e.message})", e)
            null
        }
    }

    override suspend fun isSessionActive(): Boolean = getCurrentSession() != null
}
