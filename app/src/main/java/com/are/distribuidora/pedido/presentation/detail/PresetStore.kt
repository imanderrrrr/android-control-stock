package com.are.distribuidora.pedido.presentation.detail

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste presets de cantidad custom usando SharedPreferences.
 * Solo presentation; no toca dominio ni data de negocio.
 */
@Singleton
class PresetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("order_qty_presets", Context.MODE_PRIVATE)
    }

    private val basePresets = listOf(6, 12, 24)

    /** Devuelve base + custom ordenados y sin duplicados. */
    fun getPresets(): List<Int> {
        val custom = prefs.getStringSet(KEY_CUSTOM, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
        return (basePresets + custom).distinct().sorted()
    }

    /** Guarda un preset custom. Rango válido: 1..999. */
    fun saveCustomPreset(qty: Int) {
        if (qty !in 1..999) return
        val existing = prefs.getStringSet(KEY_CUSTOM, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(qty.toString())
        prefs.edit().putStringSet(KEY_CUSTOM, existing).apply()
    }

    companion object {
        private const val KEY_CUSTOM = "custom_presets"
    }
}

