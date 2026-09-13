package com.are.distribuidora.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Borrador del pedido en curso — recuperación tras un cierre inesperado.
 *
 * Es PURAMENTE LOCAL: nunca se sube a Firestore ni entra en ningún worker de sync.
 * No tiene `syncStatus` a propósito, para que sea imposible colarlo en el pipeline
 * de subida por descuido.
 *
 * La clave primaria es el `vendedorId`, así que "un solo borrador a la vez por
 * vendedor" es un invariante del esquema y no una regla que haya que recordar
 * aplicar: empezar otro pedido REEMPLAZA el anterior.
 *
 * El contrato de recuperación es: si al arrancar existe una fila aquí, es porque
 * nadie cerró el flujo bien. El borrador se borra al confirmar el pedido y al
 * abandonar el flujo explícitamente, de modo que sobrevivir a un arranque
 * significa exactamente "se cerró inesperadamente" — tanto por crash como porque
 * Android mató el proceso por memoria (que es el caso más común y NO deja reporte
 * de crash).
 */
@Entity(tableName = "pedido_draft")
data class PedidoDraftEntity(
    @PrimaryKey val vendedorId: String,
    val routeId: String,
    /** Cliente existente; null cuando la selección fue un cliente temporal. */
    val clienteId: String?,
    /** Snapshot del cliente, para poder nombrarlo en el diálogo sin ir a otra tabla. */
    val clienteNombre: String,
    val clienteTelefono: String?,
    val clienteDireccion: String?,
    /** true = ClienteSelection.Temporal; false = ClienteSelection.Existente. */
    val clienteEsTemporal: Boolean,
    /** Fecha de entrega elegida, formato "YYYY-MM-DD". */
    val deliveryDate: String,
    /** Si el vendedor había activado el IVA del 12% en el carrito. */
    val ivaEnabled: Boolean,
    /** Instante del último guardado. Decide la antigüedad del borrador. */
    val updatedAt: Long,
)

/**
 * Ítem del borrador. Espeja [com.are.distribuidora.pedido.presentation.create.CartItem]
 * con todo lo necesario para reconstruir el carrito tal cual estaba.
 *
 * La PK compuesta (vendedorId, productoId) refleja que el carrito es un mapa por
 * productId: guardar dos veces el mismo producto es imposible por construcción.
 */
@Entity(
    tableName = "pedido_draft_items",
    primaryKeys = ["vendedorId", "productoId"],
    foreignKeys = [
        ForeignKey(
            entity = PedidoDraftEntity::class,
            parentColumns = ["vendedorId"],
            childColumns = ["vendedorId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [Index(value = ["vendedorId"], unique = false)],
)
data class PedidoDraftItemEntity(
    val vendedorId: String,
    /** Id del producto, o "custom_<uuid>" para un ítem personalizado sin producto en BD. */
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    /** Monto absoluto de descuento ya calculado. */
    val descuentoAmount: Double,
    /** Porcentaje informativo (0 cuando el descuento se fijó por monto). */
    val descuentoPercent: Double,
    /** Nombre del enum [com.are.distribuidora.domain.pedido.DiscountType]. */
    val descuentoType: String,
    val notes: String?,
    val category: String?,
    val imageUrl: String?,
    val barcode: String?,
)

data class PedidoDraftWithItemsEntity(
    @Embedded val draft: PedidoDraftEntity,
    @Relation(parentColumn = "vendedorId", entityColumn = "vendedorId")
    val items: List<PedidoDraftItemEntity>,
)
