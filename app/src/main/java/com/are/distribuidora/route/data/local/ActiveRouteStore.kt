package com.are.distribuidora.route.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore propio (separado del de "auth") para la ruta activa elegida por el vendedor.
private val Context.activeRouteDataStore: DataStore<Preferences> by preferencesDataStore(name = "active_route")

/**
 * Guarda la ruta activa que el vendedor elige para su jornada.
 * Se reinicia por día: solo se considera activa si fue fijada HOY (scope "por hoy").
 */
@Singleton
class ActiveRouteStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyId = stringPreferencesKey("active_route_id")
    private val keyDate = stringPreferencesKey("active_route_date")

    /** Id de la ruta activa solo si fue fijada en [today] (yyyy-MM-dd); si no, null. */
    fun observeActiveRouteId(today: String): Flow<String?> =
        context.activeRouteDataStore.data.map { prefs ->
            if (prefs[keyDate] == today) prefs[keyId] else null
        }

    suspend fun setActiveRoute(routeId: String, today: String) {
        context.activeRouteDataStore.edit { prefs ->
            prefs[keyId] = routeId
            prefs[keyDate] = today
        }
    }

    suspend fun clear() {
        context.activeRouteDataStore.edit { prefs ->
            prefs.remove(keyId)
            prefs.remove(keyDate)
        }
    }
}
