package com.are.distribuidora.screenaccess.domain.model

/**
 * Permisos de pantalla del usuario actual.
 *
 * REGLA POR DEFECTO (crítica, debe coincidir con el panel web): una pantalla
 * AUSENTE del mapa significa PERMITIDA. Solo se DENIEGA cuando su valor es
 * explícitamente `false`. Así, un usuario sin configuración ve todo.
 *
 * @property flags solo contiene las pantallas con un valor explícito (true/false)
 *                 leído de Firestore/cache. Las ausentes se consideran permitidas.
 */
data class ScreenAccess(
    private val flags: Map<AppScreen, Boolean>,
) {
    /** ¿Puede el usuario ver [screen]? Default-allow: solo `false` explícito bloquea. */
    fun isAllowed(screen: AppScreen): Boolean = flags[screen] != false

    companion object {
        /** Sin restricciones (usado como valor inicial y cuando no hay configuración). */
        fun allowAll(): ScreenAccess = ScreenAccess(emptyMap())
    }
}
