package com.are.distribuidora.orders.domain.model

/**
 * Ítem de entrada para editar un pedido (sistema `orders` / "Otros Pedidos").
 *
 * - [itemId]: docId estable en la subcolección de Firestore. Para ítems ya existentes se
 *   conserva el itemId original; para ítems agregados se genera uno nuevo.
 * - [productId]: id de producto real, o `custom_<UUID>` para un ítem personalizado sin
 *   producto en catálogo (mismo patrón que el flujo de creación de pedidos).
 *
 * Invariante: dentro de una misma edición, [productId] es único (índice único
 * orderId+productId en Room). La capa de presentación fusiona por productId al agregar.
 */
data class EditOrderItemInput(
    val itemId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val notes: String? = null,
)
