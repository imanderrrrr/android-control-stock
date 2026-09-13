package com.are.distribuidora.roles.domain

/**
 * Permisos de la app. Son ACCIONES, no pestañas: una pestaña visible con un botón
 * que hace algo prohibido no es control de acceso.
 *
 * La única tabla rol → permisos vive en [RolePolicy]. Ningún fragment ni ViewModel
 * decide por su cuenta qué puede un rol.
 */
enum class Permission {
    /** Pantalla Inicio: ruta del día y clientes atendidos. Sin dinero. */
    VIEW_INICIO,
    /** Inventario en modo consulta: lista, búsqueda, detalle, escáner para buscar. */
    VIEW_CATALOG,
    /** Crear y editar producto (precio, foto, código), agregar stock, venta directa, borrar. */
    EDIT_PRODUCT,
    /** Vales de entrada y salida de stock (4.1). Decisión #1 del plan: solo admin por defecto. */
    CREATE_VOUCHER,
    /** Pedido nuevo completo, con impresión. */
    CREATE_ORDER,
    /** Mis pedidos con sus montos y la suma del día. */
    VIEW_OWN_ORDERS,
    /** Editar o borrar un pedido propio (además exige vendedorId == uid). */
    EDIT_OWN_ORDER,
    /** Otros pedidos: lista, detalle, monto por pedido, impresión. */
    VIEW_ALL_ORDERS,
    /** Suma del día de una ruta que no es la propia (encabezado de grupo en Otros pedidos). */
    VIEW_ROUTE_TOTALS,
    /** Editar o borrar pedidos ajenos. */
    EDIT_ANY_ORDER,
    /** Cuentas por cobrar: consulta. */
    VIEW_RECEIVABLES,
    /** Registrar un cobro y marcar pagada. */
    COLLECT_RECEIVABLE,
    /** Crear, editar y borrar cuentas por cobrar. */
    MANAGE_RECEIVABLES,
    /** Pestaña Reportes. */
    VIEW_REPORTS,
    /** Pestaña Clientes: crear, editar, límite de crédito, asignar ruta. */
    MANAGE_CLIENTS,
    /** Crear y editar rutas. */
    MANAGE_ROUTES,
}
