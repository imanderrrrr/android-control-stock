package com.are.distribuidora.screenaccess.domain.model

import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.roles.domain.Role
import com.are.distribuidora.roles.domain.RolePolicy

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
    /**
     * Rol de negocio (4.0/4.1). A diferencia de las pantallas, el rol es default-RESTRICTIVO:
     * sin documento o sin campo `role` ⇒ [Role.VENDEDOR].
     */
    val role: Role = Role.VENDEDOR,
) {
    /** ¿Puede el usuario ver [screen]? Default-allow: solo `false` explícito bloquea. */
    fun isAllowed(screen: AppScreen): Boolean = flags[screen] != false

    /** ¿Tiene el usuario el [permission] según la única tabla [RolePolicy]? */
    fun can(permission: Permission): Boolean = RolePolicy.allows(role, permission)

    companion object {
        /** Sin restricciones de pantalla (valor inicial); el rol sigue siendo vendedor por defecto. */
        fun allowAll(): ScreenAccess = ScreenAccess(emptyMap())
    }
}
