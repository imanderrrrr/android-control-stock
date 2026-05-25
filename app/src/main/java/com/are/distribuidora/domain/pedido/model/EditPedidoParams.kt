package com.are.distribuidora.domain.pedido.model

/**
 * Parámetros para editar un pedido existente.
 *
 * [itemsToUpsert]   — ítems nuevos o modificados (se insertan o actualizan con upsert).
 * [itemIdsToDelete] — IDs de ítems que el usuario eliminó (soft delete: isDeleted=true).
 * [previousItems]   — snapshot de los ítems ANTES de editar; usado para calcular el delta
 *                     de stock (restaurar/descontar la diferencia por producto).
 * [descuentoGlobal] — descuento global del pedido (puede cambiar o mantenerse igual).
 * [vendedorId]      — UID del vendedor (para versioning y auditoría).
 */
data class EditPedidoParams(
    val pedidoId: String,
    val vendedorId: String,
    val clienteId: String?,
    val itemsToUpsert: List<EditPedidoItemInput>,
    val itemIdsToDelete: List<String>,
    val previousItems: List<PreviousItemSnapshot>,
    val descuentoGlobal: Double,
)

/**
 * Snapshot mínimo de un ítem del pedido antes de editarlo.
 * Solo se necesita productoId + cantidad para calcular el delta de stock.
 */
data class PreviousItemSnapshot(
    val itemId: String,
    val productoId: String,
    val cantidad: Int,
)

data class EditPedidoItemInput(
    /** null = ítem nuevo; non-null = ítem existente que se actualiza. */
    val itemId: String?,
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoItem: Double,
    val notes: String? = null,
)

