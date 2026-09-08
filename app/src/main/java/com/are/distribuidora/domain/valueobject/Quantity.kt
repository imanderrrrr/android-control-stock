package com.are.distribuidora.domain.valueobject

@JvmInline
value class Quantity private constructor(val value: Int) {

    companion object {
        /**
         * Desde 4.1 se PERMITE stock negativo: el libro de movimientos es la verdad y un
         * vendedor puede entregar antes de que exista el vale de entrada. El negativo es
         * información (se muestra en rojo), no un error.
         */
        fun of(value: Int): Quantity = Quantity(value)

        fun zero(): Quantity = Quantity(0)
    }

    operator fun plus(other: Quantity): Quantity = of(this.value + other.value)

    operator fun minus(other: Quantity): Quantity = of(this.value - other.value)

    fun isNegative(): Boolean = value < 0

    operator fun compareTo(other: Quantity): Int = this.value.compareTo(other.value)

    fun isZero(): Boolean = value == 0
}
