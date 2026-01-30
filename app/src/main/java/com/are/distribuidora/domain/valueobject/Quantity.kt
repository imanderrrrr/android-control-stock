package com.are.distribuidora.domain.valueobject

@JvmInline
value class Quantity private constructor(val value: Int) {

    companion object {
        fun of(value: Int): Quantity {
            require(value >= 0) { "Stock no puede ser negativo: $value" }
            return Quantity(value)
        }

        fun zero(): Quantity = Quantity(0)
    }

    operator fun plus(other: Quantity): Quantity =
        // Allow temporary 0 in intermediate calculation if semantic allows, 
        // but here we just sum values. 0 + 5 = 5.
        // If both are > 0, result > 0.
        if (this.value == 0 && other.value == 0) zero()
        else of(this.value + other.value)

    operator fun minus(other: Quantity): Quantity {
        val result = this.value - other.value
        require(result >= 0) { "Resulting quantity cannot be negative" }
        return if (result == 0) zero() else of(result)
    }

    operator fun compareTo(other: Quantity): Int = this.value.compareTo(other.value)

    fun isZero(): Boolean = value == 0
}
