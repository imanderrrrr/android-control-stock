package com.are.distribuidora.auth.domain.repository

import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.core.result.Result

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Session>
    suspend fun logout(): Result<Unit>

    /**
     * Solo lectura local. Nunca llama al remoto.
     */
    suspend fun getCurrentSession(): Session?

    /**
     * True si existe Session en local (independiente del authToken).
     */
    suspend fun isSessionActive(): Boolean
}
