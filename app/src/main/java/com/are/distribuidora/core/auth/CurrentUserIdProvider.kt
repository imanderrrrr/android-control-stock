package com.are.distribuidora.core.auth

/**
 * Abstracción mínima para obtener el UID del usuario autenticado actualmente.
 * Permite tests sin dependencia de FirebaseAuth.
 */
interface CurrentUserIdProvider {
    /** Retorna el UID del usuario autenticado, o null si no hay sesión. */
    fun get(): String?
}

