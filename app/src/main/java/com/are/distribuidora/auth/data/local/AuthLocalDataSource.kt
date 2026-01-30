package com.are.distribuidora.auth.data.local

import com.are.distribuidora.auth.domain.model.Session

interface AuthLocalDataSource {
    suspend fun saveSession(session: Session)
    suspend fun getSession(): Session?
    suspend fun clearSession()
}
