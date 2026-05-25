package com.are.distribuidora.domain.pedido.model

data class CreatePedidoItemInput(
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoItem: Double,
    val notes: String? = null,
)
