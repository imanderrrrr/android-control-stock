package com.are.distribuidora.roles.domain

/**
 * Rol de negocio del usuario. Se guarda como `role` en `userScreenAccess/{uid}`
 * (lo escribe el panel web / Admin SDK) y se cachea en Room.
 *
 * Sin documento, sin campo o valor desconocido ⇒ [VENDEDOR] (mínimo privilegio).
 */
enum class Role(val key: String) {
    ADMIN("admin"),
    VENDEDOR("vendedor");

    companion object {
        /** Resuelve la clave persistida; cualquier valor desconocido o nulo es [VENDEDOR]. */
        fun fromKey(key: String?): Role =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: VENDEDOR
    }
}
