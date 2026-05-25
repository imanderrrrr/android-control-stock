package com.are.distribuidora.core.money

import kotlin.math.roundToLong

/**
 * Redondeo al múltiplo de Q 0.25 más cercano.
 *
 * Regla monetaria de Guatemala: se manejan monedas de 25, 50 y 75 centavos.
 *   0 – 12 centavos  → 0
 *  13 – 37 centavos  → 25
 *  38 – 62 centavos  → 50
 *  63 – 87 centavos  → 75
 *  88 – 99 centavos  → 100 (sube 1 quetzal)
 *
 * Ejemplo: Q 45.16 → Q 45.25 | Q 45.39 → Q 45.50 | Q 45.57 → Q 45.50 | Q 45.89 → Q 46.00
 *
 * Ubicada en `core` porque es una regla monetaria transversal compartida por todos
 * los contextos de negocio (pedidos, ventas, etc.).
 */
object RoundToQuarterQuetzalUseCase {

    /**
     * Redondea [amount] al múltiplo de 0.25 más cercano.
     * @param amount monto en quetzales (puede tener decimales arbitrarios)
     * @return monto redondeado (múltiplo exacto de 0.25)
     */
    operator fun invoke(amount: Double): Double {
        // Multiplicar por 4 → redondear al entero más cercano → dividir entre 4
        return (amount * 4.0).roundToLong() / 4.0
    }
}

