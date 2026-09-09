package com.are.distribuidora.stockmovement.domain.model

/**
 * Sentido físico de un movimiento de inventario.
 * La cantidad del movimiento SIEMPRE es positiva; el signo lo da el tipo.
 */
enum class MovementType {
    ENTRADA,
    SALIDA;

    /** +1 para entradas, -1 para salidas. */
    val sign: Int get() = if (this == ENTRADA) 1 else -1

    companion object {
        fun fromDelta(delta: Int): MovementType = if (delta >= 0) ENTRADA else SALIDA
    }
}

/**
 * Motivo del movimiento.
 *
 * - Los motivos AUTOMÁTICOS los genera la app al confirmar/editar/borrar un pedido y llevan
 *   siempre [StockMovement.orderId].
 * - Los motivos MANUALES son los "vales" que registra una persona desde Inventario.
 */
enum class MovementReason(val isAutomatic: Boolean) {
    PEDIDO(true),
    PEDIDO_EDICION(true),
    PEDIDO_BORRADO(true),
    COMPRA(false),
    DEVOLUCION(false),
    AJUSTE(false),
    MERMA(false),
    OTRO(false);

    companion object {
        /** Motivos que puede elegir una persona en la pantalla "Nuevo vale". */
        val manual: List<MovementReason> = entries.filter { !it.isAutomatic }

        fun fromNameOrNull(name: String?): MovementReason? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Un asiento del libro de movimientos de inventario.
 *
 * Es INMUTABLE por diseño: nunca se edita ni se borra. Un error se corrige con un movimiento
 * contrario de motivo [MovementReason.AJUSTE]. Así el libro es auditable y el stock de cada
 * producto es, en todo momento, la suma con signo de sus movimientos desde el último reinicio.
 *
 * @property id            Identificador. Determinístico para los automáticos (ver [StockMovementIds]),
 *                         UUID para los vales manuales.
 * @property quantity      Unidades, siempre > 0.
 * @property orderId       Solo en movimientos automáticos: id del pedido que lo originó.
 * @property createdBy     uid de quien lo generó.
 * @property createdByName Nombre/correo de quien lo generó (snapshot para el historial).
 * @property createdAt     Hora local del teléfono. En Firestore se sustituye por serverTimestamp.
 */
data class StockMovement(
    val id: String,
    val productId: String,
    val productName: String,
    val type: MovementType,
    val quantity: Int,
    val reason: MovementReason,
    val orderId: String? = null,
    val note: String? = null,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Long,
) {
    init {
        require(quantity > 0) { "quantity debe ser > 0 (fue $quantity)" }
        require(productId.isNotBlank()) { "productId requerido" }
        if (reason.isAutomatic) {
            require(!orderId.isNullOrBlank()) { "Los movimientos automáticos requieren orderId" }
        }
    }

    /** Efecto sobre el contador de stock: +quantity para entradas, -quantity para salidas. */
    val signedQuantity: Int get() = type.sign * quantity
}

/**
 * Ids determinísticos para los movimientos automáticos.
 *
 * Un reintento del worker que sube un pedido vuelve a generar exactamente el mismo id, y el
 * servidor escribe el movimiento solo si no existe (create). Así un reintento nunca descuenta
 * dos veces.
 */
object StockMovementIds {
    /** Movimiento de un ítem al confirmar (versión 1) o editar (versión N) un pedido. */
    fun forOrderItem(orderId: String, itemId: String, version: Int): String =
        "ord_${orderId}_${itemId}_v$version"

    /** Movimiento compensatorio de un ítem al borrar un pedido. */
    fun forOrderItemDeletion(orderId: String, itemId: String): String =
        "ord_${orderId}_${itemId}_del"

    /**
     * Movimiento por diferencia de una edición del pipeline B ("Otros pedidos"): los pedidos
     * ajenos no llevan `version`, así que se usa un contador local de ediciones.
     */
    fun forOtherOrderEdit(orderId: String, itemId: String, editVersion: Int): String =
        "oth_${orderId}_${itemId}_e$editVersion"

    /** Ítems personalizados (`custom_<uuid>`) no existen en catálogo: no generan movimiento. */
    fun isCatalogProduct(productId: String): Boolean =
        productId.isNotBlank() && !productId.startsWith("custom_")
}
