package com.are.distribuidora.data.venta

data class SaleDocument(
    val client: SaleClientDocument,
    val items: List<SaleItemDocument>
)

data class SaleClientDocument(
    val name: String,
    val clientId: String? = null,
    val address: String? = null,
)

data class SaleItemDocument(
    val productId: Long,
    val quantity: Int,
    val unitPrice: Double
)
