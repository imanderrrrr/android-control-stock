package com.are.distribuidora.core.utils

import java.security.MessageDigest

/**
 * Utilidad para calcular el orderKey determinístico de negocio.
 *
 * El orderKey identifica un "pedido equivalente": misma ruta, misma fecha de entrega,
 * mismo cliente y mismo vendedor. Si el negocio decide bloquear incluso entre vendedores
 * distintos, basta con eliminar `vendedorId` del input (documentar ese cambio en un ADR).
 *
 * Algoritmo:
 *   orderKey = SHA-256( routeId + "|" + deliveryDate + "|" + clientId + "|" + vendedorId )
 *   resultado en hexadecimal lowercase (64 chars).
 *
 * Propiedades:
 * - Determinístico: misma entrada → misma salida siempre.
 * - Colisión prácticamente imposible (SHA-256 = 2^256 combinaciones).
 * - Sin dependencias externas: usa java.security.MessageDigest disponible en Android.
 */
object OrderKeyUtil {

    /**
     * Calcula el orderKey para un pedido.
     *
     * @param routeId      ID de la ruta (no puede estar en blanco).
     * @param deliveryDate Fecha de entrega en formato "YYYY-MM-DD" (no puede estar en blanco).
     * @param clientId     ID único del cliente (usar vacío "" para clientes temporales → sin deduplicación).
     * @param vendedorId   UID del vendedor creador.
     * @return             String hex lowercase de 64 caracteres, o null si clientId está en blanco
     *                     (clientes temporales no son deduplicables por diseño).
     */
    fun compute(
        routeId: String,
        deliveryDate: String,
        clientId: String,
        vendedorId: String,
    ): String? {
        // Solo clientes con ID estable pueden ser deduplicados
        if (clientId.isBlank()) return null
        if (routeId.isBlank()) return null
        if (deliveryDate.isBlank()) return null

        val input = "$routeId|$deliveryDate|$clientId|$vendedorId"
        return sha256Hex(input)
    }

    /** Calcula SHA-256 y retorna el resultado en hexadecimal lowercase. */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}

