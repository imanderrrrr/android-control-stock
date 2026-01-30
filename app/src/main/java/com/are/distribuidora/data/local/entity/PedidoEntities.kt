package com.are.distribuidora.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.are.distribuidora.data.local.SyncStatus

@Entity(
    tableName = "pedidos",
    indices = [Index(value = ["vendedorId"], unique = false)]
)
data class PedidoEntity(
    @PrimaryKey val id: String,
    val vendedorId: String,
    val clienteId: String?,
    val subtotal: Double,
    val descuentoGlobal: Double,
    val total: Double,
    val estado: String, // Snapshot del estado para infraestructura; dominio define el enum
    val version: Int,
    val actualizadoPor: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "pedido_items",
    foreignKeys = [
        ForeignKey(
            entity = PedidoEntity::class,
            parentColumns = ["id"],
            childColumns = ["pedidoId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["pedidoId"], unique = false), Index(value = ["productoId"], unique = false)]
)
data class PedidoItemEntity(
    @PrimaryKey val id: String,
    val pedidoId: String,
    val productoId: String,
    val nombre: String,
    val precioUnitario: Double,
    val cantidad: Int,
    val descuentoItem: Double,
    val totalItem: Double,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

