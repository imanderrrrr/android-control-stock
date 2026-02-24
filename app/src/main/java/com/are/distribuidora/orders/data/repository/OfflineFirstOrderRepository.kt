package com.are.distribuidora.orders.data.repository

import android.util.Log
import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.data.local.OrderLocalDataSource
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemEntity
import com.are.distribuidora.orders.data.local.entity.OrderItemStagingEntity
import com.are.distribuidora.orders.data.mapper.toDomain
import com.are.distribuidora.orders.data.remote.OrderRemoteDataSource
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderItem
import com.are.distribuidora.orders.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val currentUserIdProvider: CurrentUserIdProvider,
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

        // ── Opción B: excluir pedidos propios ──────────────────────────────
        // Reglas:
        //   - Si uid == null (sesión cerrada): no filtrar nada.
        //   - Si vendedorId == null/blank en el header: conservar (pedido legacy / unknown).
        //   - Si vendedorId == uid: EXCLUIR (pedido propio).
        //   - Cualquier otro caso: conservar.
        val uid = currentUserIdProvider.get()

        // PURGA: eliminar headers propios ya persistidos (datos viejos de ejecuciones anteriores).
        // Estrictamente acotada a routeId + deliveryDate + vendedorId == miUid.
        if (!uid.isNullOrBlank()) {
            try {
                local.deleteOwnHeaders(routeId = routeId, deliveryDate = deliveryDate, vendedorId = uid, now = now)
            } catch (e: Exception) {
                Log.w(tag, "fetchOrdersHeader: purga deleteOwnHeaders falló (${e.message})")
                // No abortar: continuar con el fetch aunque la purga falle.
            }
        }

        val filteredHeaders = if (uid.isNullOrBlank()) {
            Log.d(tag, "fetchOrdersHeader: uid null/blank, sin filtro Opción B")
            headers
        } else {
            val excluded = headers.count { h -> !h.vendedorId.isNullOrBlank() && h.vendedorId == uid }
            val included = headers.size - excluded
            Log.i(tag, "fetchOrdersHeader: OpciónB total=${headers.size} excluidos=$excluded incluidos=$included routeId=$routeId")
            headers.filter { h -> h.vendedorId.isNullOrBlank() || h.vendedorId != uid }
        }

        // ── Filtro isDeleted: excluir pedidos eliminados remotamente ──────
        // Post-filtro local: si el header remoto tiene isDeleted=true, lo descartamos
        // y purgamos el local si ya existía.
        // No usamos whereEqualTo en Firestore para evitar depender de índices compuestos
        // (mismo patrón que productos/clientes).
        val nonDeletedHeaders = filteredHeaders.filter { dto -> !dto.isDeleted }
        val deletedInRemote = filteredHeaders.filter { dto -> dto.isDeleted }

        if (deletedInRemote.isNotEmpty()) {
            Log.i(tag, "fetchOrdersHeader: ${deletedInRemote.size} headers con isDeleted=true; purgar locales routeId=$routeId")
            deletedInRemote.forEach { dto ->
                try {
                    val orderId = dto.orderId.trim()
                    if (orderId.isNotBlank()) {
                        // Marcar local como eliminado (por si ya existe en Room)
                        local.markOrderDeleted(orderId = orderId, now = now)
                        // Limpiar items locales del pedido eliminado
                        local.deleteItemsByOrderId(orderId = orderId)
                        Log.d(tag, "fetchOrdersHeader: purgado local eliminado orderId=$orderId")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "fetchOrdersHeader: purga de eliminado falló orderId=${dto.orderId} (${e.message})")
                    // No abortar
                }
            }
        }

        if (nonDeletedHeaders.isEmpty()) {
            Log.i(tag, "fetchOrdersHeader: 0 headers tras filtros (OpciónB + isDeleted) routeId=$routeId date=$deliveryDate")
            return Result.Success(Unit)
        }

        // Guardar/actualizar SOLO cabecera. No descargar items.
        // Validación defensiva: si falta algún campo crítico, no persistimos nada para ese pedido.
        try {
            nonDeletedHeaders.forEach { dto ->
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

                // Preservar estado previo si ya existe en Room para no sobreescribir
                // un pedido ya COMPLETED o IN_PROGRESS con datos vacíos.
                val existing = try { local.getOrderById(orderId) } catch (_: Exception) { null }

                if (existing != null && existing.downloadStatus == "COMPLETED") {
                    // Pedido ya descargado correctamente; solo actualizar metadatos de cabecera
                    // pero NO resetear downloadStatus, itemsDownloaded ni totalAmount.
                    local.upsertOrderHeader(
                        existing.copy(
                            clientName = clientName,
                            clientAddress = dto.clientAddress,
                            sellerName = dto.sellerName,
                            itemsCount = dto.itemsCount,
                            vendedorId = dto.vendedorId,
                            updatedAt = now,
                        )
                    )
                    Log.i(tag, "fetchOrdersHeader: COMPLETED, solo actualiza metadatos. orderId=$orderId")
                } else {
                    // Nuevo pedido o pendiente: insertar/actualizar preservando createdAt
                    local.upsertOrderHeader(
                        OrderEntity(
                            orderId = orderId,
                            routeId = dtoRouteId,
                            deliveryDate = dtoDate,
                            clientName = clientName,
                            clientAddress = dto.clientAddress,
                            sellerName = dto.sellerName,
                            itemsCount = dto.itemsCount,
                            itemsDownloaded = existing?.itemsDownloaded ?: 0,
                            totalAmount = existing?.totalAmount,
                            downloadStatus = existing?.downloadStatus?.takeIf {
                                it == "IN_PROGRESS" || it == "FAILED"
                            } ?: "ITEMS_PENDING",
                            failedReasonCode = existing?.failedReasonCode,
                            failedReasonMessage = existing?.failedReasonMessage,
                            failedAttempts = existing?.failedAttempts ?: 0,
                            lastAttemptAt = existing?.lastAttemptAt,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            vendedorId = dto.vendedorId,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchOrdersHeader: persistencia local falló (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        }

        Log.i(tag, "fetchOrdersHeader: done routeId=$routeId date=$deliveryDate persisted=${nonDeletedHeaders.size}")
        return Result.Success(Unit)
    }

    override suspend fun fetchAllOrdersHeader(routeId: String): Result<Unit> {
        if (routeId.isBlank()) return Result.Error(Failure.ValidationError("routeId requerido"))

        val now = System.currentTimeMillis()

        val headers = try {
            remote.fetchAllOrderHeaders(routeId = routeId)
        } catch (e: Exception) {
            Log.w(tag, "fetchAllOrdersHeader: remoto falló routeId=$routeId (${e.message})")
            return Result.Error(Failure.NetworkError)
        }

        if (headers.isEmpty()) {
            Log.i(tag, "fetchAllOrdersHeader: 0 headers routeId=$routeId")
            return Result.Success(Unit)
        }

        val uid = currentUserIdProvider.get()

        // Purga: eliminar headers propios ya persistidos para esta ruta
        if (!uid.isNullOrBlank()) {
            try {
                local.deleteAllOwnHeadersByRoute(routeId = routeId, vendedorId = uid, now = now)
            } catch (e: Exception) {
                Log.w(tag, "fetchAllOrdersHeader: purga deleteAllOwnHeadersByRoute falló (${e.message})")
            }
        }

        // Filtro Opción B: excluir pedidos propios
        val filteredHeaders = if (uid.isNullOrBlank()) {
            headers
        } else {
            val excluded = headers.count { h -> !h.vendedorId.isNullOrBlank() && h.vendedorId == uid }
            Log.i(tag, "fetchAllOrdersHeader: OpciónB total=${headers.size} excluidos=$excluded routeId=$routeId")
            headers.filter { h -> h.vendedorId.isNullOrBlank() || h.vendedorId != uid }
        }

        // Filtro isDeleted: excluir eliminados remotamente y purgar locales
        val nonDeletedHeaders = filteredHeaders.filter { dto -> !dto.isDeleted }
        val deletedInRemote = filteredHeaders.filter { dto -> dto.isDeleted }

        if (deletedInRemote.isNotEmpty()) {
            deletedInRemote.forEach { dto ->
                try {
                    val orderId = dto.orderId.trim()
                    if (orderId.isNotBlank()) {
                        local.markOrderDeleted(orderId = orderId, now = now)
                        local.deleteItemsByOrderId(orderId = orderId)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "fetchAllOrdersHeader: purga eliminado falló orderId=${dto.orderId} (${e.message})")
                }
            }
        }

        if (nonDeletedHeaders.isEmpty()) {
            Log.i(tag, "fetchAllOrdersHeader: 0 headers tras filtros routeId=$routeId")
            return Result.Success(Unit)
        }

        try {
            nonDeletedHeaders.forEach { dto ->
                val orderId = dto.orderId.trim()
                val dtoRouteId = dto.routeId.trim()
                val clientName = dto.clientName.trim()

                if (orderId.isBlank() || dtoRouteId.isBlank() || clientName.isBlank()) {
                    Log.w(tag, "fetchAllOrdersHeader: header incompleto; skip orderId='${dto.orderId}'")
                    return@forEach
                }

                if (dto.itemsCount <= 0) {
                    Log.w(tag, "fetchAllOrdersHeader: pedido sin items; skip orderId=$orderId")
                    return@forEach
                }

                val existing = try { local.getOrderById(orderId) } catch (_: Exception) { null }

                if (existing != null && existing.downloadStatus == "COMPLETED") {
                    local.upsertOrderHeader(
                        existing.copy(
                            clientName = clientName,
                            clientAddress = dto.clientAddress,
                            sellerName = dto.sellerName,
                            itemsCount = dto.itemsCount,
                            vendedorId = dto.vendedorId,
                            updatedAt = now,
                        )
                    )
                } else {
                    local.upsertOrderHeader(
                        OrderEntity(
                            orderId = orderId,
                            routeId = dtoRouteId,
                            deliveryDate = dto.deliveryDate,
                            clientName = clientName,
                            clientAddress = dto.clientAddress,
                            sellerName = dto.sellerName,
                            itemsCount = dto.itemsCount,
                            itemsDownloaded = existing?.itemsDownloaded ?: 0,
                            totalAmount = existing?.totalAmount,
                            downloadStatus = existing?.downloadStatus?.takeIf {
                                it == "IN_PROGRESS" || it == "FAILED"
                            } ?: "ITEMS_PENDING",
                            failedReasonCode = existing?.failedReasonCode,
                            failedReasonMessage = existing?.failedReasonMessage,
                            failedAttempts = existing?.failedAttempts ?: 0,
                            lastAttemptAt = existing?.lastAttemptAt,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            vendedorId = dto.vendedorId,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "fetchAllOrdersHeader: persistencia local falló (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        }

        Log.i(tag, "fetchAllOrdersHeader: done routeId=$routeId persisted=${nonDeletedHeaders.size}")
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

        // ── Guard rail: bloquear descarga de pedidos eliminados ────────────
        // Protege contra datos viejos en Room o navegación directa por rutas alternativas.
        if (order.isDeleted) {
            Log.w(tag, "downloadOrderItems: SKIP deleted orderId=$orderId")
            return Result.Error(Failure.ValidationError("ORDER_DELETED"))
        }

        // ── Guard rail Opción B: nunca descargar items de un pedido propio ──
        // Protege contra datos viejos en Room o navegación directa por rutas alternativas.
        val uidForGuard = currentUserIdProvider.get()
        if (!uidForGuard.isNullOrBlank()) {
            val vendedorId = order.vendedorId
            if (!vendedorId.isNullOrBlank() && vendedorId == uidForGuard) {
                Log.w(tag, "downloadOrderItems: SKIP own orderId=$orderId vendedorId=$vendedorId")
                return Result.Error(Failure.ValidationError("OWN_ORDER_EXCLUDED"))
            }
        }

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
                    itemId = first.itemId,
                    productId = productId,
                    productName = first.productName,
                    unitPrice = first.unitPrice,
                    quantity = totalQty,
                )
            }

        // expectedItemsCount: cuántos ítems únicos esperamos en staging.
        // Se usa normalizedItems.size porque el proceso de deduplicación puede reducir
        // el conteo respecto a order.itemsCount (que refleja docs brutos en Firestore).
        // Validar contra order.itemsCount causaría COUNT_MISMATCH falso positivo cuando
        // Firestore envía duplicados de productId — bug #4.
        val expectedItemsCount = normalizedItems.size

        // Guardar staging (limpiar primero para evitar duplicados)
        try {
            local.clearStaging(orderId)
            local.insertStaging(
                normalizedItems.map { dto ->
                    OrderItemStagingEntity(
                        itemId = dto.itemId,
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

        if (stagingCount != expectedItemsCount) {
            // NO persistir items finales si no coincide con los ítems únicos normalizados.
            Log.w(tag, "downloadOrderItems: COUNT_MISMATCH orderId=$orderId staging=$stagingCount expectedNormalized=$expectedItemsCount rawFromHeader=${order.itemsCount}")
            try {
                local.clearStaging(orderId)
                local.markFailed(
                    orderId = orderId,
                    now = now,
                    reasonCode = "COUNT_MISMATCH",
                    reasonMessage = "Items incompletos: staging=$stagingCount expectedNormalized=$expectedItemsCount",
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
                    itemId = st.itemId,
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
                itemsDownloaded = expectedItemsCount,
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

    override suspend fun deleteOrder(routeId: String, orderId: String): Result<Unit> {
        if (routeId.isBlank()) return Result.Error(Failure.ValidationError("routeId requerido"))
        if (orderId.isBlank()) return Result.Error(Failure.ValidationError("orderId requerido"))

        val now = System.currentTimeMillis()
        val uid = currentUserIdProvider.get()

        // 1) Soft delete en Firestore (NO borra físicamente)
        try {
            remote.markOrderDeleted(routeId = routeId, orderId = orderId, deletedByUid = uid)
        } catch (e: Exception) {
            Log.e(tag, "deleteOrder: remoto falló orderId=$orderId (${e.message})", e)
            return Result.Error(Failure.NetworkError)
        }

        // 2) Soft delete local: marcar isDeleted=true en Room
        try {
            local.markOrderDeleted(orderId = orderId, now = now)
        } catch (e: Exception) {
            Log.e(tag, "deleteOrder: markOrderDeleted local falló orderId=$orderId (${e.message})", e)
            return Result.Error(Failure.DatabaseError)
        }

        // 3) Eliminar items locales (final + staging) para que el detalle no quede accesible offline.
        // Equivalente al patrón de productos: markAsPendingDelete + limpieza downstream.
        try {
            local.deleteItemsByOrderId(orderId = orderId)
        } catch (e: Exception) {
            // No abortar: el header ya está marcado como eliminado.
            // Items huérfanos serán ignorados porque el header tiene isDeleted=true.
            Log.w(tag, "deleteOrder: deleteItemsByOrderId falló orderId=$orderId (${e.message})")
        }

        Log.i(tag, "deleteOrder: ok orderId=$orderId routeId=$routeId uid=$uid")
        return Result.Success(Unit)
    }

    override fun observeOrdersByRoute(routeId: String): Flow<List<Order>> {
        val uid = currentUserIdProvider.get()
        return local.observeByRoute(routeId).map { entities ->
            entities
                .filter { e ->
                    // Excluir pedidos del propio vendedor (Opción B)
                    if (!uid.isNullOrBlank() && !e.vendedorId.isNullOrBlank()) {
                        e.vendedorId != uid
                    } else true
                }
                .map { it.toDomain() }
        }
    }

    override fun observeOrdersByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<Order>> {
        val uid = currentUserIdProvider.get()
        return local.observeByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate).map { entities ->
            entities
                .filter { e ->
                    // Excluir pedidos del propio vendedor (Opción B)
                    if (!uid.isNullOrBlank() && !e.vendedorId.isNullOrBlank()) {
                        e.vendedorId != uid
                    } else true
                }
                .map { it.toDomain() }
        }
    }

    override suspend fun getOrderById(orderId: String): Order? {
        if (orderId.isBlank()) return null
        return try {
            local.getOrderById(orderId)
                ?.takeIf { !it.isDeleted }
                ?.toDomain()
        } catch (e: Exception) {
            Log.w(tag, "getOrderById: error orderId=$orderId (${e.message})")
            null
        }
    }

    override suspend fun getItemsByOrderId(orderId: String): List<OrderItem> {
        if (orderId.isBlank()) return emptyList()
        return try {
            local.getItemsByOrderId(orderId).map { it.toDomain() }
        } catch (e: Exception) {
            Log.w(tag, "getItemsByOrderId: error orderId=$orderId (${e.message})")
            emptyList()
        }
    }
}
