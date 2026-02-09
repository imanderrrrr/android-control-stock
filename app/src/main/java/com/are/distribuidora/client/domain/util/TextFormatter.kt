package com.are.distribuidora.client.domain.util

/**
 * Utilidades de formateo y normalización de texto para el dominio de clientes.
 *
 * Estas funciones implementan las reglas de negocio de formateo que deben aplicarse
 * antes de persistir los datos en el repositorio.
 */
object TextFormatter {

    /**
     * Capitaliza la primera letra de cada palabra.
     * Ejemplo: "juan pérez" -> "Juan Pérez"
     * Ejemplo: "JOSÉ LUIS" -> "José Luis"
     */
    fun capitalizeWords(text: String): String {
        if (text.isBlank()) return text

        return text.split(" ")
            .joinToString(" ") { word ->
                if (word.isEmpty()) {
                    word
                } else {
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
    }

    /**
     * Normaliza un número de teléfono a formato XXXX XXXX.
     * - Elimina todos los espacios existentes
     * - Toma solo dígitos
     * - Limita a 8 dígitos
     * - Inserta espacio en la posición 4
     *
     * Ejemplos:
     * - "54428542" -> "5442 8542"
     * - "5442 8542" -> "5442 8542"
     * - "5442-8542" -> "5442 8542"
     * - "544285421" -> "5442 8542" (trunca)
     */
    fun formatPhoneNumber(phone: String?): String? {
        if (phone.isNullOrBlank()) return null

        // Extraer solo dígitos
        val digitsOnly = phone.filter { it.isDigit() }

        // Validar que tenga exactamente 8 dígitos
        if (digitsOnly.length < 8) return null

        // Tomar solo los primeros 8 dígitos
        val normalized = digitsOnly.take(8)

        // Formatear como XXXX XXXX
        return "${normalized.substring(0, 4)} ${normalized.substring(4, 8)}"
    }

    /**
     * Valida que un teléfono formateado cumpla con las reglas de negocio.
     * Debe tener exactamente 8 dígitos (sin contar el espacio).
     * Formatos válidos: "12345678" o "1234 5678"
     * Formatos inválidos: "1234-5678", "123", "123456789"
     */
    fun isValidPhoneNumber(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return true // null es válido (campo opcional)

        // Solo permitir dígitos y espacios
        if (!phone.all { it.isDigit() || it == ' ' }) return false

        val digitsOnly = phone.filter { it.isDigit() }
        return digitsOnly.length == 8
    }
}

