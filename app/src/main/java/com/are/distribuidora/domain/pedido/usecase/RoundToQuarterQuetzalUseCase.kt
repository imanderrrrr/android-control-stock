package com.are.distribuidora.domain.pedido.usecase

/**
 * Delegado de retrocompatibilidad.
 *
 * La implementación canónica vive en [com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase].
 * Este objeto permanece aquí para no romper los imports existentes en el módulo `pedido`.
 *
 * Nuevo código fuera del módulo `pedido` debe importar directamente desde `core.money`.
 */
object RoundToQuarterQuetzalUseCase {
    operator fun invoke(amount: Double): Double =
        com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase(amount)
}
