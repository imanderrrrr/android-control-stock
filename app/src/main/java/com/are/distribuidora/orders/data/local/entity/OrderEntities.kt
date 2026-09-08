package com.are.distribuidora.orders.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla orders (cabecera del pedido).
 *
 * Importante:
 * - Existe aunque los items no estén descargados.
 * - Room es la fuente de verdad local.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["routeId"], unique = false),
        Index(value = ["deliveryDate"], unique = false),
    ],
)
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val routeId: String,
    val deliveryDate: String,
    val clientName: String,
    val clientAddress: String?,
    val sellerName: String?,
    val itemsCount: Int,
    val itemsDownloaded: Int = 0,
    val totalAmount: Double?,
    val downloadStatus: String,
    val failedReasonCode: String?,
    val failedReasonMessage: String?,
    val failedAttempts: Int = 0,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * UID del vendedor que creó el pedido (Firestore field: vendedorId).
     * Null para pedidos legacy que no tienen este campo.
     * Usado para filtro Opción B: excluir pedidos propios en la descarga.
     */
    val vendedorId: String? = null,
    /**
     * Soft delete: true = pedido eliminado. NO se muestra en listas ni se descargan sus items.
     * Consistente con el patrón de ProductEntity y ClientEntity.
     */
    val isDeleted: Boolean = false,
    /**
     * true = el pedido tiene ediciones locales (ítems agregados/quitados al editar un pedido
     * AJENO) pendientes de subir a Firestore. El worker de subida lo procesa y lo vuelve a
     * false al confirmar la escritura remota.
     *
     * Mientras es true, el downsync NO sobreescribe el header ni re-descarga los ítems: la
     * edición local es la fuente de verdad hasta que se sube. Preserva vendedorId/sellerName
     * (no se tocan en la subida), de modo que el pedido sigue siendo "ajeno".
     */
    val pendingUpload: Boolean = false,
    /**
     * 4.1: contador local de ediciones de este pedido ajeno. Base del id determinístico de los
     * movimientos PEDIDO_EDICION (`oth_{orderId}_{itemId}_e{editVersion}`): un reintento del
     * worker de subida reutiliza el mismo id y el servidor no vuelve a incrementar el stock.
     */
    val editVersion: Int = 0,
)

@Entity(
    tableName = "order_items",
    indices = [
        Index(value = ["orderId"], unique = false),
        Index(value = ["orderId", "productId"], unique = true),
    ],
)
data class OrderItemEntity(
    /**
     * PK estable = itemId de Firestore (docId de la subcolección items).
     * Para items legacy sin itemId se genera un UUID al insertarlos.
     */
    @PrimaryKey val itemId: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    /**
     * Descuento absoluto del ítem (Q), descargado desde Firestore (items.discountAmount).
     * Mismo campo que el vendedor creador escribe desde `pedido_items.descuentoItem`.
     * 0.0 para pedidos legacy o ítems sin descuento.
     */
    val discountAmount: Double = 0.0,
    val createdAt: Long,
    /**
     * Detalle / instrucción especial para este ítem, descargado desde Firestore.
     * Mismo campo que el vendedor creador escribe en `pedido_items.notes`.
     * Null para pedidos legacy o items sin detalle.
     */
    val notes: String? = null,
)

/**
 * Tabla temporal de staging para validar integridad antes del commit.
 * No se expone a UI.
 */
@Entity(
    tableName = "order_items_staging",
    indices = [Index(value = ["orderId"], unique = false)],
)
data class OrderItemStagingEntity(
    /**
     * PK estable = itemId de Firestore.
     * Garantiza que si se inserta el mismo item dos veces en staging, se reemplaza.
     */
    @PrimaryKey val itemId: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val discountAmount: Double = 0.0,
    val notes: String? = null,
)
