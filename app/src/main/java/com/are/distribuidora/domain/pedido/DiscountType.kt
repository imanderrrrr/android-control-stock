package com.are.distribuidora.domain.pedido

/**
 * Tipo de descuento que el vendedor puede aplicar a un ítem del carrito.
 *
 * - PERCENTAGE : el valor ingresado es un porcentaje [0–100].
 * - AMOUNT     : el valor ingresado es un monto absoluto en Quetzales (Q).
 *
 * Dominio puro — sin dependencias de Android ni de infraestructura.
 */
enum class DiscountType {
    PERCENTAGE,
    AMOUNT,
}

