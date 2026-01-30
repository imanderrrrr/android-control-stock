package com.are.distribuidora.domain.valueobject

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money private constructor(val amount: BigDecimal) {

    companion object {
        fun of(amount: BigDecimal): Money {
            require(amount >= BigDecimal.ZERO) { "Money cannot be negative" }
            return Money(amount.setScale(2, RoundingMode.HALF_UP))
        }

        fun zero(): Money = Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
    }

    operator fun plus(other: Money): Money =
        of(this.amount + other.amount)

    operator fun times(multiplier: Int): Money {
        require(multiplier >= 0) { "Multiplier must be >= 0" }
        return of(this.amount * BigDecimal(multiplier))
    }
    
    operator fun compareTo(other: Money): Int = this.amount.compareTo(other.amount)
}
