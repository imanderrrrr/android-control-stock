package com.are.distribuidora.orders.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla orders (cabecera del pedido).
 *
 * Importante:
 * - Existe aunque los items no estén descargados.
 * - Room es la fuente de verdad local.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["routeId"], unique = false),
        Index(value = ["deliveryDate"], unique = false),
    ],
)
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val routeId: String,
    val deliveryDate: String,
    val clientName: String,
    val clientAddress: String?,
    val sellerName: String?,
    val itemsCount: Int,
    val itemsDownloaded: Int = 0,
    val totalAmount: Double?,
    val downloadStatus: String,
    val failedReasonCode: String?,
    val failedReasonMessage: String?,
    val failedAttempts: Int = 0,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"], unique = false)],
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val createdAt: Long,
)

/**
 * Tabla temporal de staging para validar integridad antes del commit.
 * No se expone a UI.
 */
@Entity(
    tableName = "order_items_staging",
    indices = [Index(value = ["orderId"], unique = false)],
)
data class OrderItemStagingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
)
