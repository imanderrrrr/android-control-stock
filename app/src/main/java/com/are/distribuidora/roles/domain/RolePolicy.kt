package com.are.distribuidora.roles.domain

/**
 * Rol de negocio del usuario. Se lee de `userScreenAccess/{uid}.role` (lo escribe el panel web).
 * Sin documento o sin rol ⇒ [VENDEDOR] (default restrictivo, según el ADR de roles).
 */
enum class Role(val key: String) {
    ADMIN("admin"),
    VENDEDOR("vendedor");

    companion object {
        fun fromKey(key: String?): Role = entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: VENDEDOR
    }
}

/** Permisos de la matriz de acceso v2 (plan DailyStock 4.0, sección "Matriz de acceso v2"). */
enum class Permission {
    VIEW_INICIO,
    VIEW_CATALOG,
    EDIT_PRODUCT,
    CREATE_VOUCHER,
    CREATE_ORDER,
    VIEW_OWN_ORDERS,
    EDIT_OWN_ORDER,
    VIEW_ALL_ORDERS,
    VIEW_ROUTE_TOTALS,
    EDIT_ANY_ORDER,
    VIEW_RECEIVABLES,
    COLLECT_RECEIVABLE,
    MANAGE_RECEIVABLES,
    VIEW_REPORTS,
    MANAGE_CLIENTS,
    MANAGE_ROUTES,
}

/**
 * ÚNICA tabla rol → permisos. Cualquier gate de UI o de caso de uso consulta aquí.
 *
 * Defaults de las decisiones abiertas con Yonatan (plan 4.0, "Decisiones para Yonatan"):
 * vales solo admin (#1), vendedor cobra en sus rutas (#2), edita/borra lo suyo (#3), ve Inicio (#4).
 */
object RolePolicy {
    private val vendedor: Set<Permission> = setOf(
        Permission.VIEW_INICIO,
        Permission.VIEW_CATALOG,
        Permission.CREATE_ORDER,
        Permission.VIEW_OWN_ORDERS,
        Permission.EDIT_OWN_ORDER,
        Permission.VIEW_ALL_ORDERS,
        Permission.VIEW_RECEIVABLES,
        Permission.COLLECT_RECEIVABLE,
    )

    fun allows(role: Role, permission: Permission): Boolean = when (role) {
        Role.ADMIN -> true
        Role.VENDEDOR -> permission in vendedor
    }
}
