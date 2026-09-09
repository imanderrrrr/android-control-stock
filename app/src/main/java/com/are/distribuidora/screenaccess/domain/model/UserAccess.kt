package com.are.distribuidora.screenaccess.domain.model

import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.roles.domain.Role
import com.are.distribuidora.roles.domain.RolePolicy

/**
 * Acceso efectivo del usuario actual: rol + restricciones por pantalla del panel web.
 *
 * Reglas:
 * - [can]: el permiso lo concede el rol ([RolePolicy]). Las banderas `screens` NO
 *   quitan permisos de acción: solo ocultan pestañas.
 * - [isAllowed]: la pantalla está permitida si el rol concede alguno de sus permisos
 *   Y `screens.<x>` no es `false` explícito. `screens` solo puede restringir, nunca ampliar.
 * - Sin documento / sin rol ⇒ [leastPrivilege] (vendedor sin overrides).
 *
 * @property screenOverrides solo las pantallas con valor explícito (true/false) leído
 *   de Firestore/cache. Las ausentes no restringen.
 */
data class UserAccess(
    val role: Role,
    val screenOverrides: Map<AppScreen, Boolean> = emptyMap(),
) {
    val isAdmin: Boolean get() = role == Role.ADMIN

    fun can(permission: Permission): Boolean = RolePolicy.grants(role, permission)

    fun isAllowed(screen: AppScreen): Boolean {
        val roleAllows = RolePolicy.permissionsForScreen(screen).any { can(it) }
        return roleAllows && screenOverrides[screen] != false
    }

    companion object {
        /** Mínimo privilegio: vendedor sin restricciones adicionales. Valor por defecto. */
        fun leastPrivilege(): UserAccess = UserAccess(Role.VENDEDOR, emptyMap())

        fun admin(): UserAccess = UserAccess(Role.ADMIN, emptyMap())
    }
}
