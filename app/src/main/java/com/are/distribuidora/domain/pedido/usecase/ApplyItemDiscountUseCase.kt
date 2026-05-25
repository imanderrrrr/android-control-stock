package com.are.distribuidora.domain.pedido.usecase

import javax.inject.Inject

/**
 * Regla de negocio: convierte un descuento en porcentaje al monto absoluto
 * que se descuenta del subtotal de un ítem del carrito.
 *
 * Arquitectura limpia: esta regla pertenece al dominio porque determina
 * cuánto dinero pierde el vendedor en la transacción.
 *
 * Fórmula: descuentoMonto = precioUnitario × cantidad × (porcentaje / 100)
 *
 * El resultado es el valor que se almacena en [CreatePedidoItemInput.descuentoItem]
 * y en [PedidoItem.descuentoItem].
 */
class ApplyItemDiscountUseCase @Inject constructor() {

    /**
     * @param precioUnitario precio unitario del producto (snapshot)
     * @param cantidad       cantidad de unidades en el ítem
     * @param porcentaje     descuento en % [0.0 – 100.0]
     * @return monto absoluto de descuento, redondeado a 2 decimales. Nunca negativo.
     * @throws IllegalArgumentException si porcentaje está fuera del rango [0, 100]
     */
    operator fun invoke(
        precioUnitario: Double,
        cantidad: Int,
        porcentaje: Double,
    ): Double {
        require(porcentaje in 0.0..100.0) {
            "El porcentaje de descuento debe estar entre 0 y 100, recibido: $porcentaje"
        }
        require(cantidad >= 0) { "La cantidad no puede ser negativa" }
        require(precioUnitario >= 0.0) { "El precio unitario no puede ser negativo" }

        val subtotalBase = precioUnitario * cantidad
        val monto = subtotalBase * (porcentaje / 100.0)

        // Redondear a 2 decimales para evitar errores de punto flotante
        return Math.round(monto * 100.0) / 100.0
    }
}

