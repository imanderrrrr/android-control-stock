package com.are.distribuidora.auth.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.are.distribuidora.auth.domain.model.Session
import kotlinx.coroutines.flow.first

/**
 * Persistencia local de Session vía DataStore (Preferences).
 *
 * Decisiones:
 * - Si falla persistencia/lectura, dejamos que la excepción propague al repositorio.
 * - No se valida authToken aquí: se guarda tal cual (puede ser null o inválido).
 */
class DataStoreAuthLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) : AuthLocalDataSource {

    private object Keys {
        val USER_ID = stringPreferencesKey("session_user_id")
        val EMAIL = stringPreferencesKey("session_email")
        val AUTH_TOKEN = stringPreferencesKey("session_auth_token")
        val LAST_LOGIN_AT = longPreferencesKey("session_last_login_at")
    }

    override suspend fun saveSession(session: Session) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            // DataStore Preferences no soporta null: si es null, removemos.
            if (session.authToken.isNullOrBlank()) {
                prefs.remove(Keys.AUTH_TOKEN)
            } else {
                prefs[Keys.AUTH_TOKEN] = session.authToken
            }
            prefs[Keys.LAST_LOGIN_AT] = session.lastLoginAt
        }
    }

    override suspend fun getSession(): Session? {
        val prefs = dataStore.data.first()
        val userId = prefs[Keys.USER_ID] ?: return null
        val email = prefs[Keys.EMAIL] ?: return null
        val lastLoginAt = prefs[Keys.LAST_LOGIN_AT] ?: return null
        val token = prefs[Keys.AUTH_TOKEN]

        return Session(
            userId = userId,
            email = email,
            authToken = token,
            lastLoginAt = lastLoginAt,
        )
    }

    override suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.LAST_LOGIN_AT)
        }
    }
}
