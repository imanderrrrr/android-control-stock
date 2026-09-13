package com.are.distribuidora.screenaccess.domain.model

/**
 * Pantallas de la app cuyo acceso puede conceder/revocar un administrador desde
 * el panel web.
 *
 * IMPORTANTE: el [key] debe coincidir EXACTAMENTE con las claves que el panel web
 * guarda en `userScreenAccess/{uid}.screens` (ver `src/config/screens.ts` del repo
 * del panel). No renombrar.
 */
enum class AppScreen(val key: String) {
    INICIO("inicio"),
    INVENTARIO("inventario"),
    PEDIDOS("pedidos"),
    CLIENTES("clientes"),
    REPORTES("reportes"),
    CUENTAS_PENDIENTES("cuentasPendientes");

    companion object {
        /** Devuelve la pantalla cuyo [key] coincide, o null si la clave es desconocida. */
        fun fromKey(key: String?): AppScreen? = entries.firstOrNull { it.key == key }
    }
}
