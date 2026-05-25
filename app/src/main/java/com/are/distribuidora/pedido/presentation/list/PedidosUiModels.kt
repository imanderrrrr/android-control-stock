package com.are.distribuidora.pedido.presentation.list

/**
 * Modelos de UI para la pantalla de Pedidos.
 * Solo datos ya mapeados y formateados para la UI; sin lógica de negocio.
 */

/** Tarjeta de ruta en la pantalla principal de Pedidos (Mis Pedidos). */
data class RouteOrderSummaryUiModel(
    val routeId: String,
    val routeName: String,           // routeId si no hay nombre disponible
    val pedidoCount: Int,
    val totalFormatted: String,      // "Q 1,250.00" — suma de todos los pedidos de la ruta
)

/** Tarjeta de cliente dentro de una ruta (Mis Pedidos). */
data class PedidoClienteUiModel(
    val pedidoId: String,
    val clienteNombre: String,
    val totalFormatted: String,      // "Q 150.00"
    val itemCount: Int,
    val syncStatus: String,          // texto corto: "✓ Sincronizado", "⏳ Pendiente"…
    val creadoEnFormatted: String,   // "20 Feb 2026"
    /**
     * true si el pedido puede editarse.
     * Un pedido NO es editable si está marcado como PENDING_DELETE o isDeleted.
     * Cualquier otro estado (PENDING_CREATE, PENDING_UPDATE, SYNCED, FAILED) sí es editable.
     */
    val isEditable: Boolean = true,
)

/** Ítem del detalle del pedido (vista carrito de solo lectura). */
data class PedidoItemUiModel(
    val itemId: String,              // UUID del PedidoItem — clave única para DiffUtil
    val nombre: String,
    val precioUnitario: String,      // "Q 25.00"
    val cantidad: Int,
    val descuentoFormatted: String?, // null si sin descuento
    val totalItem: String,           // "Q 50.00"
    val notes: String? = null,       // detalle/instrucción especial del cliente para este ítem
    val imageUrl: String? = null,    // URL/URI de imagen del producto (remota o local)
)

// ── Otros Pedidos ─────────────────────────────────────────────────────────────

/**
 * Tarjeta de ruta en la sección "Otros Pedidos".
 * Agrupa los headers de pedidos de otros vendedores por ruta.
 */
data class OtrosRouteUiModel(
    val routeId: String,
    val routeName: String,
    val orderCount: Int,
    val orders: List<OtrosOrderHeaderUiModel>,
    /**
     * Suma de los totalAmount de pedidos ya descargados en esta ruta.
     * Null si ningún pedido tiene total disponible aún.
     */
    val routeTotalFormatted: String?,
)

/**
 * Header de pedido de otro vendedor.
 * Muestra información básica y un botón de descarga de items.
 */
data class OtrosOrderHeaderUiModel(
    val orderId: String,
    val routeId: String,
    val clientName: String,
    val clientAddress: String?,
    val sellerName: String?,
    val itemsCount: Int,
    val downloadStatusLabel: String,  // "Pendiente", "Descargando…", "Descargado", "Error"
    val downloadStatus: OtrosDownloadUiStatus,
    val totalFormatted: String?,      // null si aún no se descargaron items
    val deliveryDate: String,         // "YYYY-MM-DD" — fecha de entrega del pedido
    val createdAtFormatted: String,   // "20 Feb 2026" — fecha de creación formateada
)

enum class OtrosDownloadUiStatus {
    PENDING,       // ITEMS_PENDING — se puede pulsar descargar
    IN_PROGRESS,   // IN_PROGRESS   — spinner activo
    COMPLETED,     // COMPLETED     — ya descargado
    FAILED,        // FAILED        — se puede reintentar
}

