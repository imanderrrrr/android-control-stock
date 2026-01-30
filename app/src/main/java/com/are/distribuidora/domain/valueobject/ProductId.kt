package com.are.distribuidora.domain.valueobject

@JvmInline
value class ProductId private constructor(val value: String) {

    companion object {
        fun of(raw: String): ProductId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) {
                "Invalid ProductId: vacío"
            }
            return ProductId(normalized)
        }
    }
}
