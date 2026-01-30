package com.are.distribuidora.domain.model

/**
 * Modelo soberano de dominio.
 *
 * - No depende de Room/Firestore/Android.
 * - Representa la verdad del negocio para una venta.
 */
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity

data class Sale(
    val id: String,
    val date: Long,
    val total: Money,
    val items: List<SaleItem>,
)

data class SaleItem(
    val productId: ProductId,
    val quantity: Quantity,
    val price: Money,
)
