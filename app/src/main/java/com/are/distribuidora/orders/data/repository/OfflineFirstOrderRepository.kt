package com.are.distribuidora.orders.data.repository

import android.util.Log
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.mapper.toDomain
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Repositorio offline-first para pedidos.
 *
 * Reglas clave:
 * - Firestore es solo lectura (distribución).
 * - Room es la única fuente de verdad local.
 * - Descarga robusta: header primero, items bajo demanda, staging + validación antes del commit.
 */
class OfflineFirstOrderRepository(
    private val local: OrderLocalDataSource,
    private val remote: OrderRemoteDataSource,
) : OrderRepository {

    private val tag = "OrdersSync"

    override suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String): Result<Unit> {
        if (routeId.isBlank()) return Result.Error(Failure.ValidationError("routeId requerido"))
        if (deliveryDate.isBlank()) return Result.Error(Failure.ValidationError("deliveryDate requerido"))

        val now = System.currentTimeMillis()

        val headers = try {
            remote.fetchOrderHeaders(routeId = routeId, deliveryDate = deliveryDate)
        } catch (e: Exception) {
            Log.w(tag, "fetchOrdersHeader: remoto falló routeId=$routeId date=$deliveryDate (${e.message})")
            return Result.Error(Failure.NetworkError)
        }

        if (headers.isEmpty()) {
            Log.i(tag, "fetchOrdersHeader: 0 headers routeId=$routeId date=$deliveryDate")
            return Result.Success(Unit)
        }

        // Guardar/actualizar SOLO cabecera. No descargar items.
        // Validación defensiva: si falta algún campo crítico, no persistimos nada para ese pedido.
        try {
            headers.forEach { dto ->
                val orderId = dto.orderId.trim()
                val dtoRouteId = dto.routeId.trim()
                val dtoDate = dto.deliveryDate.trim()
                val clientName = dto.clientName.trim()

                if (orderId.isBlank() || dtoRouteId.isBlank() || dtoDate.isBlank() || clientName.isBlank()) {
                    Log.w(
                        tag,
                        "fetchOrdersHeader: header incompleto; skip. orderId='${dto.orderId}' routeId='${dto.routeId}' date='${dto.deliveryDate}' clientName='${dto.clientName}'",
                    )
                    return@forEach
                }

                // Caso límite: pedido sin items (itemsCount <= 0) => no persistir header ni items.
                if (dto.itemsCount <= 0) {
                    Log.w(tag, "fetchOrdersHeader: pedido sin items; skip header. orderId=$orderId itemsCount=${dto.itemsCount}")
                    return@forEach
                }

                local.upsertOrderHeader(
                    OrderEntity(
                        orderId = orderId,
                        routeId = dtoRouteId,
                        deliveryDate = dtoDate,
                        clientName = clientName,
                        clientAddress = dto.clientAddress,
                        sellerName = dto.sellerName,
                        itemsCount = dto.itemsCount,
                        itemsDownloaded = 0,
                        totalAmount = null,
                        downloadStatus = "ITEMS_PENDING",
                        failedReasonCode = null,
                        failedReasonMessage = null,
                        failedAttempts = 0,
                        lastAttemptAt = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchOrdersHeader: persistencia local falló (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        }

        Log.i(tag, "fetchOrdersHeader: done routeId=$routeId date=$deliveryDate")
        return Result.Success(Unit)
    }

    override suspend fun downloadOrderItems(orderId: String): Result<Unit> {
        if (orderId.isBlank()) return Result.Error(Failure.ValidationError("orderId requerido"))

        val now = System.currentTimeMillis()

        val order = try {
            local.getOrderById(orderId)
        } catch (e: Exception) {
            Log.e(tag, "downloadOrderItems: error leyendo header local orderId=$orderId (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        } ?: return Result.Error(Failure.NotFound)

        // Idempotencia/reintentos: si ya está COMPLETED y consistente, retornar éxito sin duplicar.
        if (order.downloadStatus == "COMPLETED" && order.itemsDownloaded >= order.itemsCount && order.itemsCount > 0) {
            Log.i(tag, "downloadOrderItems: already COMPLETED; skip. orderId=$orderId")
            return Result.Success(Unit)
        }

        // Caso límite: header inconsistente (no descargable)
        if (order.itemsCount <= 0) {
            Log.w(tag, "downloadOrderItems: header sin itemsCount válido; abort. orderId=$orderId itemsCount=${order.itemsCount}")
            // No persistimos nada adicional. Best-effort: limpiar staging por si quedó sucio.
            try { local.clearStaging(orderId) } catch (_: Exception) {}
            return Result.Success(Unit)
        }

        // Marcar IN_PROGRESS antes de hablar con Firestore
        try {
            local.markInProgress(orderId = orderId, now = now)
        } catch (e: Exception) {
            Log.e(tag, "downloadOrderItems: markInProgress falló orderId=$orderId (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        }

        // Descargar items desde Firestore usando routeId del header
        val remoteItems = try {
            remote.fetchOrderItems(routeId = order.routeId, orderId = orderId)
        } catch (e: Exception) {
            // Best-effort: dejar evidencia local
            Log.w(tag, "downloadOrderItems: remoto falló orderId=$orderId (${e.message})")
            try {
                local.clearStaging(orderId)
                local.markFailed(orderId = orderId, now = now, reasonCode = "NETWORK", reasonMessage = e.message ?: "Network error")
            } catch (_: Exception) {
                // ignore
            }
            return Result.Error(Failure.NetworkError)
        }

        // Caso 1: pedido sin items (lista vacía o null => emptyList()) => NO persistir y dejar listo para reintento.
        if (remoteItems.isEmpty()) {
            Log.w(tag, "downloadOrderItems: pedido sin items en remoto; no se persiste. orderId=$orderId")
            try {
                local.clearStaging(orderId)
                // Reutilizamos NETWORK para no introducir nuevos códigos.
                local.markFailed(orderId = orderId, now = now, reasonCode = "NETWORK", reasonMessage = "Pedido sin items")
            } catch (_: Exception) {
                // ignore
            }
            return Result.Success(Unit)
        }

        // Caso 6: duplicados por red o backend: dedupe por productId (sin crear duplicados en DB).
        val normalizedItems = remoteItems
            .filter { it.productId.isNotBlank() && it.productName.isNotBlank() && it.quantity > 0 && it.unitPrice >= 0.0 }
            .groupBy { it.productId }
            .map { (productId, group) ->
                val first = group.first()
                // Combinar duplicados sumando quantity.
                val totalQty = group.sumOf { it.quantity }
                OrderRemoteDataSource.OrderItemDto(
                    productId = productId,
                    productName = first.productName,
                    unitPrice = first.unitPrice,
                    quantity = totalQty,
                )
            }

        // Guardar staging (limpiar primero para evitar duplicados)
        try {
            local.clearStaging(orderId)
            local.insertStaging(
                normalizedItems.map { dto ->
                    OrderItemStagingEntity(
                        orderId = orderId,
                        productId = dto.productId,
                        productName = dto.productName,
                        unitPrice = dto.unitPrice,
                        quantity = dto.quantity,
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "downloadOrderItems: error escribiendo staging orderId=$orderId (${e.message})", e)
            // Caso 3: descarga parcial (DB) => rollback staging+estado
            try {
                local.clearStaging(orderId)
                local.markFailed(orderId = orderId, now = now, reasonCode = "DB", reasonMessage = e.message ?: "Database error")
            } catch (_: Exception) {
                // ignore
            }
            return Result.Error(Failure.DatabaseError)
        }

        // Validar integridad antes de commit
        val stagingCount = try {
            local.countStaging(orderId)
        } catch (e: Exception) {
            Log.e(tag, "downloadOrderItems: countStaging falló orderId=$orderId (${e.message})", e)
            try { local.clearStaging(orderId) } catch (_: Exception) {}
            return Result.Error(Failure.DatabaseError)
        }

        if (stagingCount != order.itemsCount) {
            // NO persistir items finales si no coincide
            Log.w(tag, "downloadOrderItems: COUNT_MISMATCH orderId=$orderId staging=$stagingCount expected=${order.itemsCount}")
            try {
                local.clearStaging(orderId)
                local.markFailed(
                    orderId = orderId,
                    now = now,
                    reasonCode = "COUNT_MISMATCH",
                    reasonMessage = "Items incompletos: staging=$stagingCount expected=${order.itemsCount}",
                )
            } catch (_: Exception) {
                // ignore
            }
            return Result.Error(Failure.ValidationError("Items incompletos"))
        }

        // Commit: staging -> final (transacción)
        return try {
            val staging = local.getStaging(orderId)
            if (staging.isEmpty()) {
                Log.w(tag, "downloadOrderItems: staging vacío tras validar count; abort. orderId=$orderId")
                try {
                    local.clearStaging(orderId)
                    // Reutilizamos DB para no introducir nuevos códigos.
                    local.markFailed(orderId = orderId, now = now, reasonCode = "DB", reasonMessage = "Staging vacío")
                } catch (_: Exception) {}
                return Result.Success(Unit)
            }

            val totalAmount = staging.sumOf { it.unitPrice * it.quantity }

            val finalItems = staging.map { st ->
                OrderItemEntity(
                    orderId = orderId,
                    productId = st.productId,
                    productName = st.productName,
                    unitPrice = st.unitPrice,
                    quantity = st.quantity,
                    createdAt = now,
                )
            }

            local.commitItems(
                orderId = orderId,
                finalItems = finalItems,
                totalAmount = totalAmount,
                itemsDownloaded = order.itemsCount,
                now = now,
            )

            Log.i(tag, "downloadOrderItems: COMPLETED orderId=$orderId items=${finalItems.size} total=$totalAmount")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "downloadOrderItems: commit falló orderId=$orderId (${e.message})", e)
            try {
                local.clearStaging(orderId)
                local.markFailed(orderId = orderId, now = now, reasonCode = "DB", reasonMessage = e.message ?: "Database error")
            } catch (_: Exception) {
                // ignore
            }
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<Order> {
        if (routeId.isBlank() || deliveryDate.isBlank()) return emptyList()

        val entities = try {
            local.getOrdersByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate)
        } catch (_: Exception) {
            return emptyList()
        }

        return entities.map { it.toDomain() }
    }
}
