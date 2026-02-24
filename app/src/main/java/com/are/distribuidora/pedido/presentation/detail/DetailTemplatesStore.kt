package com.are.distribuidora.pedido.presentation.detail

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persiste plantillas de detalle (textos libres) usando SharedPreferences.
 * Solo capa presentation; no toca dominio ni data de negocio.
 */
@Singleton
class DetailTemplatesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("order_detail_templates", Context.MODE_PRIVATE)
    }

    private val _templates = MutableStateFlow<List<String>>(emptyList())

    init {
        _templates.value = loadFromPrefs()
    }

    /** Flow con las plantillas actuales. */
    fun templatesFlow(): Flow<List<String>> = _templates.asStateFlow()

    /** Devuelve la lista actual sin Flow. */
    fun getTemplates(): List<String> = _templates.value

    /**
     * Agrega una plantilla nueva.
     * Validaciones: trim, no vacía, max 120 chars, única (case-insensitive).
     */
    fun addTemplate(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > MAX_LENGTH) return
        val current = _templates.value
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = current + trimmed
        persist(updated)
        _templates.value = updated
    }

    /** Elimina una plantilla por texto exacto. */
    fun deleteTemplate(text: String) {
        val updated = _templates.value.filter { it != text }
        persist(updated)
        _templates.value = updated
    }

    private fun loadFromPrefs(): List<String> {
        val set = prefs.getStringSet(KEY_TEMPLATES, emptySet()) ?: emptySet()
        // Guardar también índice de orden
        val orderRaw = prefs.getString(KEY_ORDER, "") ?: ""
        if (orderRaw.isNotBlank()) {
            val ordered = orderRaw.split(SEPARATOR)
                .filter { it.isNotBlank() && it in set }
            val remaining = set.filter { it !in ordered }
            return ordered + remaining
        }
        return set.toList()
    }

    private fun persist(list: List<String>) {
        prefs.edit()
            .putStringSet(KEY_TEMPLATES, list.toSet())
            .putString(KEY_ORDER, list.joinToString(SEPARATOR))
            .apply()
    }

    companion object {
        private const val KEY_TEMPLATES = "detail_templates"
        private const val KEY_ORDER     = "detail_templates_order"
        private const val SEPARATOR     = "|||"
        const val MAX_LENGTH = 120
    }
}

