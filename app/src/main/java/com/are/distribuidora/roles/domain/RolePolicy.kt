package com.are.distribuidora.roles.domain

import com.are.distribuidora.screenaccess.domain.model.AppScreen
import java.util.EnumSet

/**
 * ÚNICA tabla rol → permisos (matriz de acceso v2, plan "DailyStock 4.0"; ajuste 4.1.4: el
 * vendedor también crea cuentas por cobrar y registra vales de ENTRADA).
 *
 * Agregar un tercer rol es una fila aquí y un valor en el panel web.
 * `RolePolicyTest` tiene una aserción por celda: cualquier cambio debe reflejarse allí.
 */
object RolePolicy {

    private val ADMIN: Set<Permission> = EnumSet.allOf(Permission::class.java)

    private val VENDEDOR: Set<Permission> = EnumSet.of(
        Permission.VIEW_INICIO,
        Permission.VIEW_CATALOG,
        Permission.CREATE_ORDER,
        Permission.VIEW_OWN_ORDERS,
        Permission.EDIT_OWN_ORDER,
        Permission.VIEW_ALL_ORDERS,
        Permission.VIEW_RECEIVABLES,
        Permission.COLLECT_RECEIVABLE,
        // 4.1.4: registra la deuda en la calle; editar el monto o borrarla sigue siendo del admin.
        Permission.CREATE_RECEIVABLE,
        // 4.1.4: suma lo que recibe o le devuelven; las SALIDAS libres siguen siendo del admin.
        Permission.CREATE_INBOUND_VOUCHER,
    )

    fun permissionsFor(role: Role): Set<Permission> = when (role) {
        Role.ADMIN -> ADMIN
        Role.VENDEDOR -> VENDEDOR
    }

    fun grants(role: Role, permission: Permission): Boolean =
        permissionsFor(role).contains(permission)

    /** Alias de [grants] (nombre usado por la rama 4.1). */
    fun allows(role: Role, permission: Permission): Boolean = grants(role, permission)

    /**
     * Permisos que habilitan cada pestaña/sección. Una pantalla está permitida si el
     * rol concede AL MENOS UNO de ellos (y ninguna bandera `screens` la niega).
     */
    fun permissionsForScreen(screen: AppScreen): Set<Permission> = when (screen) {
        AppScreen.INICIO -> EnumSet.of(Permission.VIEW_INICIO)
        AppScreen.INVENTARIO -> EnumSet.of(Permission.VIEW_CATALOG)
        AppScreen.PEDIDOS -> EnumSet.of(
            Permission.CREATE_ORDER, Permission.VIEW_OWN_ORDERS, Permission.VIEW_ALL_ORDERS,
        )
        // La pestaña Clientes también aloja la entrada a Cuentas por cobrar: un vendedor
        // sin MANAGE_CLIENTS pero con VIEW_RECEIVABLES ve allí solo los cobros.
        AppScreen.CLIENTES -> EnumSet.of(Permission.MANAGE_CLIENTS, Permission.VIEW_RECEIVABLES)
        AppScreen.REPORTES -> EnumSet.of(Permission.VIEW_REPORTS)
        AppScreen.CUENTAS_PENDIENTES -> EnumSet.of(Permission.VIEW_RECEIVABLES)
    }
}
