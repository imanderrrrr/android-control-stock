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
    /**
     * Vale de ENTRADA (compra, devolución, ajuste al alza): SUMA inventario. 4.1.4: también el
     * vendedor, que es quien recibe producto y devoluciones en la calle. Sin esto los pedidos
     * solo restaban y nadie salvo el admin podía sumar (179/450 productos en negativo el 17/09).
     */
    CREATE_INBOUND_VOUCHER,
    /**
     * Vale de SALIDA (merma, ajuste a la baja, otro): RESTA inventario sin pedido de por medio.
     * Solo admin: dejarlo libre sería una vía para descuadrar el inventario sin rastro de venta.
     */
    CREATE_OUTBOUND_VOUCHER,
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
    /** Crear una cuenta por cobrar: registrar la deuda es el trabajo diario del vendedor (4.1.4). */
    CREATE_RECEIVABLE,
    /**
     * Editar el monto/datos de una cuenta por cobrar y borrarla. Equivale a hacer desaparecer
     * dinero, así que se queda con el admin (criterio: riesgo del dinero, no comodidad).
     */
    MANAGE_RECEIVABLES,
    /** Pestaña Reportes. */
    VIEW_REPORTS,
    /** Pestaña Clientes: crear, editar, límite de crédito, asignar ruta. */
    MANAGE_CLIENTS,
    /** Crear y editar rutas. */
    MANAGE_ROUTES,
}
