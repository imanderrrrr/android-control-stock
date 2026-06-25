package com.are.distribuidora.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.are.distribuidora.data.local.SyncStatus

@Entity(
    tableName = "pedidos",
    indices = [
        Index(value = ["vendedorId"], unique = false),
        Index(value = ["orderKey"], unique = false),
    ]
)
data class PedidoEntity(
    @PrimaryKey val id: String,
    val vendedorId: String,
    val routeId: String,
    /** Fecha de entrega en formato "YYYY-MM-DD". Usada como campo de query en Firestore. */
    val deliveryDate: String = "",
    val clienteId: String?,
    val clienteNombre: String,
    val clienteTelefono: String?,
    val clienteDireccion: String?,
    val subtotal: Double,
    val descuentoGlobal: Double,
    val total: Double,
    /** Monto de IVA (12%) aplicado al pedido. 0.0 = sin IVA (por defecto). */
    @ColumnInfo(defaultValue = "0") val ivaAmount: Double = 0.0,
    val version: Int,
    val actualizadoPor: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Clave determinística de negocio: SHA-256(routeId|deliveryDate|clientId|vendedorId).
     * Null para pedidos de clientes temporales (sin clienteId) y pedidos legacy.
     * Usado para detectar duplicados antes de crear en Room y en Firestore.
     */
    val orderKey: String? = null,
    /**
     * Soft delete explícito. Consistente con OrderEntity / ProductEntity / ClientEntity.
     * - true  → pedido eliminado; no aparece en listas ni en sync ascendente.
     * - false → estado normal (default para compatibilidad con filas legacy).
     *
     * Para pedidos SYNCED la eliminación también se propaga a Firestore antes de marcar
     * este flag localmente.
     * Para pedidos PENDING_CREATE (nunca subidos) se borra físicamente de Room directamente.
     */
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "pedido_items",
    foreignKeys = [
        ForeignKey(
            entity = PedidoEntity::class,
            parentColumns = ["id"],
            childColumns = ["pedidoId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["pedidoId"], unique = false), Index(value = ["productoId"], unique = false)]
)

data class PedidoItemEntity(
    @PrimaryKey val id: String,
    val pedidoId: String,
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoItem: Double,
    val totalItem: Double,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Detalle / instrucción especial del cliente para este ítem.
     * Opcional. Se muestra en el carrito, en la factura impresa y en el PDF.
     */
    val notes: String? = null,
    /**
     * Soft delete de ítem: true = eliminado localmente.
     * El payload de sync solo incluye ítems activos (isDeleted=false).
     * Consistente con el patrón de ProductEntity / ClientEntity / PedidoEntity.
     */
    val isDeleted: Boolean = false,
)

data class PedidoWithItemsEntity(
    @androidx.room.Embedded val pedido: PedidoEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "pedidoId"
    )
    val items: List<PedidoItemEntity>
)
