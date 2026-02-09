package com.are.distribuidora.client.presentation.util

import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher

/**
 * TextWatcher que capitaliza automáticamente la primera letra de cada palabra
 * mientras el usuario escribe.
 */
class CapitalizeTextWatcher : TextWatcher {

    private var isFormatting = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // No necesitamos hacer nada antes del cambio
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // No necesitamos hacer nada durante el cambio
    }

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting || s == null) return

        isFormatting = true

        val text = s.toString()
        val formatted = capitalizeWords(text)

        if (text != formatted) {
            s.replace(0, s.length, formatted)
        }

        isFormatting = false
    }

    private fun capitalizeWords(text: String): String {
        if (text.isEmpty()) return text

        return text.split(" ")
            .joinToString(" ") { word ->
                if (word.isEmpty()) {
                    word
                } else {
                    word.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
                }
            }
    }
}

/**
 * TextWatcher que formatea automáticamente números de teléfono al formato XXXX XXXX.
 * - Permite solo dígitos
 * - Máximo 8 dígitos
 * - Inserta espacio automáticamente después del 4to dígito
 */
class PhoneNumberTextWatcher : TextWatcher {

    private var isFormatting = false
    private var previousLength = 0

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        previousLength = s?.length ?: 0
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // No necesitamos hacer nada durante el cambio
    }

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting || s == null) return

        isFormatting = true

        // Extraer solo dígitos
        val digitsOnly = s.toString().filter { it.isDigit() }

        // Limitar a 8 dígitos
        val limited = digitsOnly.take(8)

        // Formatear con espacio después del 4to dígito
        val formatted = when {
            limited.length <= 4 -> limited
            else -> "${limited.substring(0, 4)} ${limited.substring(4)}"
        }

        // Solo reemplazar si cambió
        if (s.toString() != formatted) {
            s.replace(0, s.length, formatted)
        }

        isFormatting = false
    }
}

/**
 * InputFilter que limita la entrada a un máximo de 9 caracteres
 * (8 dígitos + 1 espacio para el formato XXXX XXXX).
 */
class PhoneNumberInputFilter : InputFilter {
    override fun filter(
        source: CharSequence?,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        // Permitir solo dígitos y espacios
        val filtered = source?.filter { it.isDigit() || it == ' ' }

        // Verificar longitud máxima (9 caracteres = "XXXX XXXX")
        val currentLength = dest?.length ?: 0
        val addedLength = filtered?.length ?: 0
        val removedLength = dend - dstart
        val newLength = currentLength + addedLength - removedLength

        if (newLength > 9) {
            // Truncar para no exceder el límite
            return ""
        }

        return filtered
    }
}



