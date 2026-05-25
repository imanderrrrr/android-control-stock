package com.are.distribuidora.domain.pedido.usecase

import javax.inject.Inject

/**
 * Regla de negocio: valida y normaliza un monto absoluto de descuento en Quetzales (Q)
 * para un ítem del carrito.
 *
 * Arquitectura limpia: la regla vive en dominio porque:
 * - Impide descuentos negativos.
 * - Clamp: el monto no puede superar el subtotal base del ítem.
 * - Redondea a 2 decimales para consistencia con el resto del sistema.
 *
 * El resultado es el valor listo para almacenar en [PedidoItem.descuentoItem].
 */
class ApplyItemDiscountByAmountUseCase @Inject constructor() {

    /**
     * @param precioUnitario precio unitario del producto (snapshot).
     * @param cantidad       cantidad de unidades en el ítem.
     * @param montoDescuento monto absoluto de descuento en Q [0 – subtotalBase].
     * @return monto absoluto de descuento, clampado al subtotal base y redondeado a 2 decimales.
     * @throws IllegalArgumentException si algún argumento es inválido.
     */
    operator fun invoke(
        precioUnitario: Double,
        cantidad: Int,
        montoDescuento: Double,
    ): Double {
        require(cantidad >= 0) { "La cantidad no puede ser negativa" }
        require(precioUnitario >= 0.0) { "El precio unitario no puede ser negativo" }
        require(montoDescuento >= 0.0) { "El monto de descuento no puede ser negativo" }

        val subtotalBase = precioUnitario * cantidad
        // El descuento nunca puede ser mayor al subtotal (el ítem no puede quedar negativo)
        val clampedMonto = montoDescuento.coerceAtMost(subtotalBase)
        return Math.round(clampedMonto * 100.0) / 100.0
    }
}

