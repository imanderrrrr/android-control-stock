package com.are.distribuidora.orders.data.mapper

import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderDownloadStatus

/**
 * Mappers de capa data para Orders.
 */
internal fun OrderEntity.toDomain(): Order =
    Order(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = deliveryDate,
        clientName = clientName,
        clientAddress = clientAddress,
        sellerName = sellerName,
        itemsCount = itemsCount,
        itemsDownloaded = itemsDownloaded,
        totalAmount = totalAmount,
        downloadStatus = runCatching { OrderDownloadStatus.valueOf(downloadStatus) }.getOrDefault(OrderDownloadStatus.NONE),
        failedReasonCode = failedReasonCode,
        failedReasonMessage = failedReasonMessage,
        failedAttempts = failedAttempts,
        lastAttemptAt = lastAttemptAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
