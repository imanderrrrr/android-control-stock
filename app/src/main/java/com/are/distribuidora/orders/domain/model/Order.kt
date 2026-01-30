package com.are.distribuidora.orders.domain.model

/**
 * Pedido (cabecera).
 *
 * Nota de diseño:
 * - Este modelo representa únicamente la cabecera y metadatos necesarios para controlar la descarga
 *   descendente de items (Firestore -> Room).
 * - Room es la fuente de verdad; Firestore es solo lectura/distribución.
 */
data class Order(
    val orderId: String,
    val routeId: String,
    val deliveryDate: String, // YYYY-MM-DD
    val clientName: String,
    val clientAddress: String?,
    val sellerName: String?,
    val itemsCount: Int,
    val itemsDownloaded: Int,
    val totalAmount: Double?,
    val downloadStatus: OrderDownloadStatus,
    val failedReasonCode: String?,
    val failedReasonMessage: String?,
    val failedAttempts: Int,
    val lastAttemptAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class OrderDownloadStatus {
    NONE,
    ITEMS_PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}
