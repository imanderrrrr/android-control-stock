package com.are.distribuidora.domain.model

import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId

/**
 * Modelo soberano de dominio.
 *
 * - No depende de Room/Firestore/Android.
 * - Representa la verdad del negocio para un producto en el catálogo.
 */
data class Product(
    val id: ProductId,
    val name: String,
    val price: Money,
    val stock: Quantity,
) {

    init {
        require(name.isNotBlank()) { "name no puede estar vacío" }
    }

    fun canSell(quantity: Quantity): Boolean {
        require(!quantity.isZero()) { "quantity debe ser mayor que 0" }
        return stock >= quantity
    }

    fun sell(quantity: Quantity): Product {
        require(!quantity.isZero()) { "quantity debe ser mayor que 0" }
        return copy(stock = stock - quantity)
    }

    fun reserve(quantity: Quantity): Product =
        copy(stock = stock - quantity)

    fun release(quantity: Quantity): Product =
        copy(stock = stock + quantity)
}
