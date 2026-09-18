package com.are.distribuidora.screenaccess.domain

import com.are.distribuidora.screenaccess.domain.model.AppScreen
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.roles.domain.Role
import com.are.distribuidora.roles.domain.RolePolicy
import com.are.distribuidora.screenaccess.domain.model.UserAccess
import com.are.distribuidora.stockmovement.domain.model.MovementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Una aserción por celda de la matriz de acceso v2 (plan DailyStock 4.0; ajuste 4.1.4: el
 * vendedor crea cuentas por cobrar y registra vales de ENTRADA, nunca de SALIDA).
 * Si cambia la matriz, cambia este test a propósito.
 */
class RolePolicyTest {

    // ── admin: todo ─────────────────────────────────────────────────────────

    @Test fun `admin VIEW_INICIO si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_INICIO))
    @Test fun `admin VIEW_CATALOG si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_CATALOG))
    @Test fun `admin EDIT_PRODUCT si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.EDIT_PRODUCT))
    @Test fun `admin CREATE_INBOUND_VOUCHER si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.CREATE_INBOUND_VOUCHER))
    @Test fun `admin CREATE_OUTBOUND_VOUCHER si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.CREATE_OUTBOUND_VOUCHER))
    @Test fun `admin CREATE_ORDER si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.CREATE_ORDER))
    @Test fun `admin VIEW_OWN_ORDERS si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_OWN_ORDERS))
    @Test fun `admin EDIT_OWN_ORDER si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.EDIT_OWN_ORDER))
    @Test fun `admin VIEW_ALL_ORDERS si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_ALL_ORDERS))
    @Test fun `admin VIEW_ROUTE_TOTALS si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_ROUTE_TOTALS))
    @Test fun `admin EDIT_ANY_ORDER si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.EDIT_ANY_ORDER))
    @Test fun `admin VIEW_RECEIVABLES si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_RECEIVABLES))
    @Test fun `admin COLLECT_RECEIVABLE si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.COLLECT_RECEIVABLE))
    @Test fun `admin CREATE_RECEIVABLE si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.CREATE_RECEIVABLE))
    @Test fun `admin MANAGE_RECEIVABLES si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.MANAGE_RECEIVABLES))
    @Test fun `admin VIEW_REPORTS si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.VIEW_REPORTS))
    @Test fun `admin MANAGE_CLIENTS si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.MANAGE_CLIENTS))
    @Test fun `admin MANAGE_ROUTES si`() = assertTrue(RolePolicy.grants(Role.ADMIN, Permission.MANAGE_ROUTES))

    @Test fun `admin tiene todos los permisos definidos`() =
        assertEquals(Permission.entries.toSet(), RolePolicy.permissionsFor(Role.ADMIN))

    // ── vendedor ────────────────────────────────────────────────────────────

    @Test fun `vendedor VIEW_INICIO si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_INICIO))
    @Test fun `vendedor VIEW_CATALOG si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_CATALOG))
    @Test fun `vendedor EDIT_PRODUCT no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.EDIT_PRODUCT))
    // 4.1.4: recibe producto y devoluciones en la calle → puede SUMAR inventario.
    @Test fun `vendedor CREATE_INBOUND_VOUCHER si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.CREATE_INBOUND_VOUCHER))
    // Restar sin pedido de por medio sigue siendo del admin.
    @Test fun `vendedor CREATE_OUTBOUND_VOUCHER no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.CREATE_OUTBOUND_VOUCHER))
    @Test fun `vendedor CREATE_ORDER si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.CREATE_ORDER))
    @Test fun `vendedor VIEW_OWN_ORDERS si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_OWN_ORDERS))
    @Test fun `vendedor EDIT_OWN_ORDER si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.EDIT_OWN_ORDER))
    @Test fun `vendedor VIEW_ALL_ORDERS si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_ALL_ORDERS))
    @Test fun `vendedor VIEW_ROUTE_TOTALS no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_ROUTE_TOTALS))
    @Test fun `vendedor EDIT_ANY_ORDER no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.EDIT_ANY_ORDER))
    @Test fun `vendedor VIEW_RECEIVABLES si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_RECEIVABLES))
    @Test fun `vendedor COLLECT_RECEIVABLE si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.COLLECT_RECEIVABLE))
    // 4.1.4: registrar la deuda es su trabajo diario; bajarle el monto o borrarla, no.
    @Test fun `vendedor CREATE_RECEIVABLE si`() = assertTrue(RolePolicy.grants(Role.VENDEDOR, Permission.CREATE_RECEIVABLE))
    @Test fun `vendedor MANAGE_RECEIVABLES no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.MANAGE_RECEIVABLES))
    @Test fun `vendedor VIEW_REPORTS no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.VIEW_REPORTS))
    @Test fun `vendedor MANAGE_CLIENTS no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.MANAGE_CLIENTS))
    @Test fun `vendedor MANAGE_ROUTES no`() = assertFalse(RolePolicy.grants(Role.VENDEDOR, Permission.MANAGE_ROUTES))

    @Test fun `vendedor tiene exactamente el set acordado`() =
        assertEquals(
            setOf(
                Permission.VIEW_INICIO, Permission.VIEW_CATALOG, Permission.CREATE_ORDER,
                Permission.VIEW_OWN_ORDERS, Permission.EDIT_OWN_ORDER, Permission.VIEW_ALL_ORDERS,
                Permission.VIEW_RECEIVABLES, Permission.COLLECT_RECEIVABLE,
                Permission.CREATE_RECEIVABLE, Permission.CREATE_INBOUND_VOUCHER,
            ),
            RolePolicy.permissionsFor(Role.VENDEDOR),
        )

    // ── Sentido del vale → permiso ──────────────────────────────────────────

    @Test fun `ENTRADA exige CREATE_INBOUND_VOUCHER`() =
        assertEquals(Permission.CREATE_INBOUND_VOUCHER, MovementType.ENTRADA.requiredPermission)

    @Test fun `SALIDA exige CREATE_OUTBOUND_VOUCHER`() =
        assertEquals(Permission.CREATE_OUTBOUND_VOUCHER, MovementType.SALIDA.requiredPermission)

    // ── Role.fromKey ────────────────────────────────────────────────────────

    @Test fun `fromKey admin`() = assertEquals(Role.ADMIN, Role.fromKey("admin"))
    @Test fun `fromKey vendedor`() = assertEquals(Role.VENDEDOR, Role.fromKey("vendedor"))
    @Test fun `fromKey null es vendedor`() = assertEquals(Role.VENDEDOR, Role.fromKey(null))
    @Test fun `fromKey desconocido es vendedor`() = assertEquals(Role.VENDEDOR, Role.fromKey("gerente"))
    @Test fun `fromKey ignora mayusculas y espacios`() = assertEquals(Role.ADMIN, Role.fromKey(" Admin "))

    // ── UserAccess: pantallas ───────────────────────────────────────────────

    @Test fun `vendedor no ve Reportes ni Clientes pero si Cobros`() {
        val v = UserAccess.leastPrivilege()
        assertTrue(v.isAllowed(AppScreen.INICIO))
        assertTrue(v.isAllowed(AppScreen.INVENTARIO))
        assertTrue(v.isAllowed(AppScreen.PEDIDOS))
        // La pestaña Clientes queda habilitada solo para mostrar Cuentas por cobrar
        assertTrue(v.isAllowed(AppScreen.CLIENTES))
        assertFalse(v.can(Permission.MANAGE_CLIENTS))
        assertFalse(v.isAllowed(AppScreen.REPORTES))
        assertTrue(v.isAllowed(AppScreen.CUENTAS_PENDIENTES))
    }

    @Test fun `screens false restringe a admin sin quitarle permisos`() {
        val a = UserAccess(Role.ADMIN, mapOf(AppScreen.PEDIDOS to false))
        assertFalse(a.isAllowed(AppScreen.PEDIDOS))
        assertTrue(a.can(Permission.EDIT_ANY_ORDER))
        assertTrue(a.can(Permission.CREATE_ORDER))
    }

    @Test fun `screens true no amplia a vendedor`() {
        val v = UserAccess(Role.VENDEDOR, mapOf(AppScreen.REPORTES to true))
        assertFalse(v.isAllowed(AppScreen.REPORTES))
    }
}
