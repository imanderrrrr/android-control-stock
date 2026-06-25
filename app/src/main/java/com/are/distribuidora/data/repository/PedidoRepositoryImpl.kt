package com.are.distribuidora.data.repository

import androidx.room.withTransaction
import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.PedidoDao
import com.are.distribuidora.data.local.dao.PedidoItemDao
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.PedidoEntity
import com.are.distribuidora.data.local.entity.PedidoItemEntity
import com.are.distribuidora.data.remote.pedido.PedidoRemoteDataSource
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoItem
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.SyncStatusLabel
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.ReportParams
import com.are.distribuidora.domain.pedido.model.ReportResult
import com.are.distribuidora.domain.pedido.model.RouteSalesData
import com.are.distribuidora.domain.pedido.model.DailySalesData
import com.are.distribuidora.domain.pedido.model.TopProductData
import com.are.distribuidora.domain.pedido.model.TopClientData
import com.are.distribuidora.core.money.RoundToQuarterQuetzalUseCase
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PedidoRepositoryImpl @Inject constructor(
    private val database: DistribuidoraDatabase,
    private val pedidoDao: PedidoDao,
    private val pedidoItemDao: PedidoItemDao,
    private val productDao: ProductDao,
    private val remoteDataSource: PedidoRemoteDataSource,
    private val currentUserIdProvider: CurrentUserIdProvider,
) : PedidoRepository {

    override suspend fun createPedido(params: CreatePedidoParams): Result<String> {
        return try {
            val pedidoId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            var subtotal = 0.0
            val itemEntities = params.items.map { input ->
                val totalItem = (input.precioUnitario * input.cantidad) - input.descuentoItem
                subtotal += totalItem

                PedidoItemEntity(
                    id = UUID.randomUUID().toString(),
                    pedidoId = pedidoId,
                    productoId = input.productoId,
                    nombre = input.nombre,
                    precioUnitario = input.precioUnitario,
                    cantidad = input.cantidad,
                    descuentoItem = input.descuentoItem,
                    totalItem = totalItem,
                    notes = input.notes,
                    syncStatus = SyncStatus.PENDING_CREATE,
                    createdAt = now,
                    updatedAt = now
                )
            }

            // El total ya viene redondeado al Q 0.25 más cercano desde el UseCase de dominio.
            val total = params.totalRedondeado

            // Calcular orderKey — null para clientes temporales (sin deduplicación)
            val orderKey = com.are.distribuidora.core.utils.OrderKeyUtil.compute(
                routeId = params.routeId,
                deliveryDate = params.deliveryDate,
                clientId = params.clienteId ?: "",
                vendedorId = params.vendedorId,
            )

            val pedidoEntity = PedidoEntity(
                id = pedidoId,
                vendedorId = params.vendedorId,
                routeId = params.routeId,
                deliveryDate = params.deliveryDate,
                clienteId = params.clienteId,
                clienteNombre = params.clienteSnapshot.nombre,
                clienteTelefono = params.clienteSnapshot.telefono,
                clienteDireccion = params.clienteSnapshot.direccion,
                subtotal = subtotal,
                descuentoGlobal = params.descuentoGlobal,
                total = total,
                ivaAmount = params.ivaAmount,
                version = 1,
                actualizadoPor = params.vendedorId,
                creadoEn = now,
                actualizadoEn = now,
                syncStatus = SyncStatus.PENDING_CREATE,
                createdAt = now,
                updatedAt = now,
                orderKey = orderKey,
            )

            database.withTransaction {
                pedidoDao.insert(pedidoEntity)
                pedidoItemDao.insertAll(itemEntities)
                // Descontar stock e incrementar comprometido por la diferencia, de forma atómica.
                params.items.forEach { item ->
                    productDao.deductStockAndCommit(item.productoId, item.cantidad)
                }
            }

            Result.Success(pedidoId)
        } catch (e: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun findActivePedidoByOrderKey(orderKey: String): String? {
        return try {
            pedidoDao.findActivePedidoByOrderKey(orderKey)?.id
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun findActivePedidoByClienteAndDay(
        clienteId: String,
        creationEpochMs: Long,
    ): String? {
        return try {
            // Calcular inicio y fin del día en la zona horaria local del dispositivo
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = creationEpochMs }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDayMs = cal.timeInMillis

            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
            cal.set(java.util.Calendar.MINUTE, 59)
            cal.set(java.util.Calendar.SECOND, 59)
            cal.set(java.util.Calendar.MILLISECOND, 999)
            val endOfDayMs = cal.timeInMillis

            pedidoDao.findActivePedidoByClienteAndDay(clienteId, startOfDayMs, endOfDayMs)?.id
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>> {
        return try {
            val pedidosWithItems = pedidoDao.getPedidosWithItemsByCliente(clienteId)

            val domainPedidos = pedidosWithItems.map { relation ->
                val entity = relation.pedido
                val itemsEntity = relation.items

                val itemsDomain = itemsEntity
                    .filter { !it.isDeleted }
                    .sortedBy { it.createdAt }.map { item ->
                    PedidoItem(
                        id = item.id,
                        productoId = item.productoId,
                        nombre = item.nombre,
                        precioUnitario = item.precioUnitario,
                        cantidad = item.cantidad,
                        descuentoItem = item.descuentoItem,
                        totalItem = item.totalItem,
                        notes = item.notes,
                    )
                }

                Pedido(
                    id = entity.id,
                    vendedorId = entity.vendedorId,
                    routeId = entity.routeId,
                    deliveryDate = entity.deliveryDate,
                    clienteId = entity.clienteId,
                    clienteSnapshot = ClienteSnapshot(
                        nombre = entity.clienteNombre,
                        telefono = entity.clienteTelefono,
                        direccion = entity.clienteDireccion
                    ),
                    items = itemsDomain,
                    subtotal = entity.subtotal,
                    descuentoGlobal = entity.descuentoGlobal,
                    total = entity.total,
                    ivaAmount = entity.ivaAmount,
                    version = entity.version,
                    actualizadoPor = entity.actualizadoPor,
                    creadoEn = entity.creadoEn,
                    actualizadoEn = entity.actualizadoEn
                )
            }
            Result.Success(domainPedidos)
        } catch (e: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems> {
        val statuses = listOf(SyncStatus.PENDING_CREATE, SyncStatus.SYNCING)
        val relations = pedidoDao.getPendingPedidosWithItems(statuses, limit)
        return relations.map { relation ->
            val entity = relation.pedido
            val domainItems = relation.items
                .filter { !it.isDeleted }
                .map { item ->
                PedidoItem(
                    id = item.id,
                    productoId = item.productoId,
                    nombre = item.nombre,
                    precioUnitario = item.precioUnitario,
                    cantidad = item.cantidad,
                    descuentoItem = item.descuentoItem,
                    totalItem = item.totalItem,
                    notes = item.notes,
                )
            }
            val domainPedido = Pedido(
                id = entity.id,
                vendedorId = entity.vendedorId,
                routeId = entity.routeId,
                deliveryDate = entity.deliveryDate,
                clienteId = entity.clienteId,
                clienteSnapshot = ClienteSnapshot(
                    nombre = entity.clienteNombre,
                    telefono = entity.clienteTelefono,
                    direccion = entity.clienteDireccion
                ),
                items = domainItems,
                subtotal = entity.subtotal,
                descuentoGlobal = entity.descuentoGlobal,
                total = entity.total,
                ivaAmount = entity.ivaAmount,
                version = entity.version,
                actualizadoPor = entity.actualizadoPor,
                creadoEn = entity.creadoEn,
                actualizadoEn = entity.actualizadoEn
            )
            PedidoWithItems(pedido = domainPedido, items = domainItems)
        }
    }

    override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) {
        val pedido = pedidoWithItems.pedido
        val now = System.currentTimeMillis()

        // Marcar como SYNCING antes de subir
        database.withTransaction {
            pedidoDao.markPedidoSyncing(pedido.id, now)
            pedidoWithItems.items.forEach { item ->
                pedidoItemDao.markItemSyncing(item.id, now)
            }
        }

        try {
            val pedidoPayload = PedidoRemoteDataSource.PedidoPayload(
                vendedorId = pedido.vendedorId,
                routeId = pedido.routeId,
                deliveryDate = pedido.deliveryDate,
                clienteId = pedido.clienteId,
                clienteNombre = pedido.clienteSnapshot.nombre,
                clienteTelefono = pedido.clienteSnapshot.telefono,
                clienteDireccion = pedido.clienteSnapshot.direccion,
                subtotal = pedido.subtotal,
                descuentoGlobal = pedido.descuentoGlobal,
                total = pedido.total,
                ivaAmount = pedido.ivaAmount,
                version = pedido.version,
                actualizadoPor = pedido.actualizadoPor,
                creadoEn = pedido.creadoEn,
                actualizadoEn = pedido.actualizadoEn,
                orderKey = com.are.distribuidora.core.utils.OrderKeyUtil.compute(
                    routeId = pedido.routeId,
                    deliveryDate = pedido.deliveryDate,
                    clientId = pedido.clienteId ?: "",
                    vendedorId = pedido.vendedorId,
                ),
            )

            val itemPayloads = pedidoWithItems.items.map { item ->
                PedidoRemoteDataSource.PedidoItemPayload(
                    id = item.id,
                    productoId = item.productoId,
                    nombre = item.nombre,
                    precioUnitario = item.precioUnitario,
                    cantidad = item.cantidad,
                    descuentoItem = item.descuentoItem,
                    totalItem = item.totalItem,
                    notes = item.notes,
                )
            }

            remoteDataSource.uploadPedido(pedido.id, pedidoPayload, itemPayloads)

            // Subida exitosa → marcar SYNCED
            val syncedAt = System.currentTimeMillis()
            database.withTransaction {
                pedidoDao.markPedidoSynced(pedido.id, syncedAt)
                pedidoWithItems.items.forEach { item ->
                    pedidoItemDao.markItemSynced(item.id, syncedAt)
                }
            }
        } catch (e: Exception) {
            // Revertir a PENDING_CREATE para reintento
            val revertAt = System.currentTimeMillis()
            database.withTransaction {
                pedidoDao.revertPedidoSyncingToPendingCreate(pedido.id, revertAt)
                pedidoWithItems.items.forEach { item ->
                    pedidoItemDao.revertItemSyncingToPendingCreate(item.id, revertAt)
                }
            }
            throw e
        }
    }

    override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> {
        return pedidoDao.getAllWithItems().map { relation ->
            val entity = relation.pedido
            val domainItems = relation.items
                .filter { !it.isDeleted }
                .map { item ->
                PedidoItem(
                    id = item.id,
                    productoId = item.productoId,
                    nombre = item.nombre,
                    precioUnitario = item.precioUnitario,
                    cantidad = item.cantidad,
                    descuentoItem = item.descuentoItem,
                    totalItem = item.totalItem,
                    notes = item.notes,
                )
            }
            val domainPedido = Pedido(
                id = entity.id,
                vendedorId = entity.vendedorId,
                routeId = entity.routeId,
                deliveryDate = entity.deliveryDate,
                clienteId = entity.clienteId,
                clienteSnapshot = ClienteSnapshot(
                    nombre = entity.clienteNombre,
                    telefono = entity.clienteTelefono,
                    direccion = entity.clienteDireccion
                ),
                items = domainItems,
                subtotal = entity.subtotal,
                descuentoGlobal = entity.descuentoGlobal,
                total = entity.total,
                ivaAmount = entity.ivaAmount,
                version = entity.version,
                actualizadoPor = entity.actualizadoPor,
                creadoEn = entity.creadoEn,
                actualizadoEn = entity.actualizadoEn
            )
            val label = when (entity.syncStatus) {
                SyncStatus.SYNCED -> SyncStatusLabel.SYNCED
                SyncStatus.FAILED, SyncStatus.ERROR -> SyncStatusLabel.FAILED
                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE,
                SyncStatus.PENDING, SyncStatus.SYNCING -> SyncStatusLabel.PENDING
                else -> SyncStatusLabel.UNKNOWN
            }
            PedidoWithItems(pedido = domainPedido, items = domainItems, syncStatusLabel = label)
        }
    }

    override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> =
        pedidoDao.observeAllWithItems().map { relations ->
            relations.map { relation ->
                val entity = relation.pedido
                val domainItems = relation.items
                    .filter { !it.isDeleted }
                    .map { item ->
                    PedidoItem(
                        id = item.id,
                        productoId = item.productoId,
                        nombre = item.nombre,
                        precioUnitario = item.precioUnitario,
                        cantidad = item.cantidad,
                        descuentoItem = item.descuentoItem,
                        totalItem = item.totalItem,
                        notes = item.notes,
                    )
                }
                val domainPedido = Pedido(
                    id = entity.id,
                    vendedorId = entity.vendedorId,
                    routeId = entity.routeId,
                    deliveryDate = entity.deliveryDate,
                    clienteId = entity.clienteId,
                    clienteSnapshot = ClienteSnapshot(
                        nombre = entity.clienteNombre,
                        telefono = entity.clienteTelefono,
                        direccion = entity.clienteDireccion
                    ),
                    items = domainItems,
                    subtotal = entity.subtotal,
                    descuentoGlobal = entity.descuentoGlobal,
                    total = entity.total,
                    ivaAmount = entity.ivaAmount,
                    version = entity.version,
                    actualizadoPor = entity.actualizadoPor,
                    creadoEn = entity.creadoEn,
                    actualizadoEn = entity.actualizadoEn
                )
                val label = when (entity.syncStatus) {
                    SyncStatus.SYNCED -> SyncStatusLabel.SYNCED
                    SyncStatus.FAILED, SyncStatus.ERROR -> SyncStatusLabel.FAILED
                    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE,
                    SyncStatus.PENDING, SyncStatus.SYNCING -> SyncStatusLabel.PENDING
                    else -> SyncStatusLabel.UNKNOWN
                }
                PedidoWithItems(pedido = domainPedido, items = domainItems, syncStatusLabel = label)
            }
        }

    override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> =
        pedidoDao.observeAllWithItemsByDate(deliveryDate).map { relations ->
            relations.map { relation ->
                val entity = relation.pedido
                val domainItems = relation.items
                    .filter { !it.isDeleted }
                    .map { item ->
                        PedidoItem(
                            id             = item.id,
                            productoId     = item.productoId,
                            nombre         = item.nombre,
                            precioUnitario = item.precioUnitario,
                            cantidad       = item.cantidad,
                            descuentoItem  = item.descuentoItem,
                            totalItem      = item.totalItem,
                            notes          = item.notes,
                        )
                    }
                val domainPedido = Pedido(
                    id              = entity.id,
                    vendedorId      = entity.vendedorId,
                    routeId         = entity.routeId,
                    deliveryDate    = entity.deliveryDate,
                    clienteId       = entity.clienteId,
                    clienteSnapshot = ClienteSnapshot(
                        nombre    = entity.clienteNombre,
                        telefono  = entity.clienteTelefono,
                        direccion = entity.clienteDireccion
                    ),
                    items            = domainItems,
                    subtotal         = entity.subtotal,
                    descuentoGlobal  = entity.descuentoGlobal,
                    total            = entity.total,
                    ivaAmount        = entity.ivaAmount,
                    version          = entity.version,
                    actualizadoPor   = entity.actualizadoPor,
                    creadoEn         = entity.creadoEn,
                    actualizadoEn    = entity.actualizadoEn
                )
                val label = when (entity.syncStatus) {
                    SyncStatus.SYNCED -> SyncStatusLabel.SYNCED
                    SyncStatus.FAILED, SyncStatus.ERROR -> SyncStatusLabel.FAILED
                    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE,
                    SyncStatus.PENDING, SyncStatus.SYNCING -> SyncStatusLabel.PENDING
                    else -> SyncStatusLabel.UNKNOWN
                }
                PedidoWithItems(pedido = domainPedido, items = domainItems, syncStatusLabel = label)
            }
        }

    override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> =
        pedidoDao.observeWithItemsById(pedidoId).map { relation ->
            relation?.let {
                val entity = it.pedido
                val domainItems = it.items
                    .filter { item -> !item.isDeleted }
                    .map { item ->
                        PedidoItem(
                            id             = item.id,
                            productoId     = item.productoId,
                            nombre         = item.nombre,
                            precioUnitario = item.precioUnitario,
                            cantidad       = item.cantidad,
                            descuentoItem  = item.descuentoItem,
                            totalItem      = item.totalItem,
                            notes          = item.notes,
                        )
                    }
                val domainPedido = entityToDomain(entity)
                val label = entityToSyncLabel(entity)
                PedidoWithItems(pedido = domainPedido, items = domainItems, syncStatusLabel = label)
            }
        }

    override suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems> {
        val statuses = listOf(SyncStatus.PENDING_UPDATE)
        val relations = pedidoDao.getPendingPedidosWithItems(statuses, limit)
        return relations.map { relation ->
            val entity = relation.pedido
            val activeItems = relation.items
                .filter { !it.isDeleted }
                .map { item ->
                    PedidoItem(
                        id             = item.id,
                        productoId     = item.productoId,
                        nombre         = item.nombre,
                        precioUnitario = item.precioUnitario,
                        cantidad       = item.cantidad,
                        descuentoItem  = item.descuentoItem,
                        totalItem      = item.totalItem,
                        notes          = item.notes,
                    )
                }
            PedidoWithItems(pedido = entityToDomain(entity), items = activeItems)
        }
    }

    override suspend fun editPedido(params: EditPedidoParams): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()

            // Verificar que el pedido existe antes de modificarlo
            val existing = pedidoDao.getById(params.pedidoId)
                ?: return Result.Error(Failure.NotFound)

            // Recalcular totales (itemsToUpsert ya son solo los activos)
            val subtotal = params.itemsToUpsert
                .sumOf { (it.precioUnitario * it.cantidad) - it.descuentoItem }
            val netAfterDiscount = (subtotal - params.descuentoGlobal).coerceAtLeast(0.0)
            // Conservar el estado de IVA del pedido original: si tenía IVA, se recalcula al 12%.
            val applyIva = existing.ivaAmount > 0.0
            val total = RoundToQuarterQuetzalUseCase(
                if (applyIva) netAfterDiscount * 1.12 else netAfterDiscount
            )
            val ivaAmount = if (applyIva) (total - netAfterDiscount).coerceAtLeast(0.0) else 0.0

            // Construir entidades de ítems para upsert
            val itemEntities = params.itemsToUpsert.map { input ->
                val totalItem = (input.precioUnitario * input.cantidad) - input.descuentoItem
                PedidoItemEntity(
                    id             = input.itemId ?: UUID.randomUUID().toString(),
                    pedidoId       = params.pedidoId,
                    productoId     = input.productoId,
                    nombre         = input.nombre,
                    precioUnitario = input.precioUnitario,
                    cantidad       = input.cantidad,
                    descuentoItem  = input.descuentoItem,
                    totalItem      = totalItem,
                    notes          = input.notes,
                    syncStatus     = SyncStatus.PENDING_UPDATE,
                    createdAt      = now,
                    updatedAt      = now,
                    isDeleted      = false,
                )
            }

            // ── Calcular deltas de stock ──────────────────────────────────────
            // previousItems es el snapshot de los ítems ANTES de guardar.
            // Para cada producto calculamos: delta = cantidadNueva - cantidadAnterior
            //   delta > 0  → el pedido consume MÁS   → descontar del stock
            //   delta < 0  → el pedido consume MENOS → restaurar al stock
            //   delta == 0 → sin cambio
            // Ítems eliminados (itemIdsToDelete): restaurar toda su cantidad.
            // Ítems nuevos (existingItemId == null): descontar toda su cantidad.

            val prevByItemId = params.previousItems.associateBy { it.itemId }

            // Ítems a eliminar: restaurar su cantidad completa
            val deleteRestores: Map<String, Int> = params.itemIdsToDelete
                .mapNotNull { itemId -> prevByItemId[itemId] }
                .groupBy { it.productoId }
                .mapValues { (_, snaps) -> snaps.sumOf { it.cantidad } }

            // Ítems upsert: calcular delta respecto al anterior (o 0 si es nuevo)
            data class StockDelta(val productoId: String, val delta: Int)
            val upsertDeltas: List<StockDelta> = params.itemsToUpsert.map { input ->
                val prevQty = if (input.itemId != null) prevByItemId[input.itemId]?.cantidad ?: 0 else 0
                StockDelta(input.productoId, input.cantidad - prevQty)
            }
            // Agrupa por producto sumando deltas (puede haber varios ítems del mismo producto)
            val upsertDeltaByProducto: Map<String, Int> = upsertDeltas
                .groupBy { it.productoId }
                .mapValues { (_, ds) -> ds.sumOf { it.delta } }

            database.withTransaction {
                // 1) Upsert ítems activos (nuevos o modificados)
                pedidoItemDao.insertAll(itemEntities)

                // 2) Soft-delete ítems que el usuario eliminó
                params.itemIdsToDelete.forEach { itemId ->
                    pedidoItemDao.markItemDeleted(itemId, now)
                }

                // 3) Marcar pedido como PENDING_UPDATE con nuevos totales
                pedidoDao.markPedidoPendingUpdate(
                    id        = params.pedidoId,
                    subtotal  = subtotal,
                    total     = total,
                    ivaAmount = ivaAmount,
                    updatedAt = now,
                )

                // 4) Restaurar stock de ítems eliminados
                deleteRestores.forEach { (productoId, qty) ->
                    if (qty > 0) productDao.restoreStock(productoId, qty)
                }

                // 5) Aplicar delta de stock por ítems modificados / nuevos
                upsertDeltaByProducto.forEach { (productoId, delta) ->
                    when {
                        delta > 0 -> productDao.deductStockAndCommit(productoId, delta)
                        delta < 0 -> productDao.restoreStock(productoId, -delta)
                        // delta == 0 → sin cambio
                    }
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }

    override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) {
        val pedido = pedidoWithItems.pedido
        val now = System.currentTimeMillis()

        // Marcar como SYNCING antes de subir
        database.withTransaction {
            pedidoDao.markPedidoSyncing(pedido.id, now)
            pedidoWithItems.items.forEach { item ->
                pedidoItemDao.markItemSyncing(item.id, now)
            }
        }

        try {
            val pedidoPayload = PedidoRemoteDataSource.PedidoPayload(
                vendedorId       = pedido.vendedorId,
                routeId          = pedido.routeId,
                deliveryDate     = pedido.deliveryDate,
                clienteId        = pedido.clienteId,
                clienteNombre    = pedido.clienteSnapshot.nombre,
                clienteTelefono  = pedido.clienteSnapshot.telefono,
                clienteDireccion = pedido.clienteSnapshot.direccion,
                subtotal         = pedido.subtotal,
                descuentoGlobal  = pedido.descuentoGlobal,
                total            = pedido.total,
                ivaAmount        = pedido.ivaAmount,
                version          = pedido.version + 1,
                actualizadoPor   = pedido.vendedorId,
                creadoEn         = pedido.creadoEn,
                actualizadoEn    = now,
                orderKey         = com.are.distribuidora.core.utils.OrderKeyUtil.compute(
                    routeId      = pedido.routeId,
                    deliveryDate = pedido.deliveryDate,
                    clientId     = pedido.clienteId ?: "",
                    vendedorId   = pedido.vendedorId,
                ),
            )

            val itemPayloads = pedidoWithItems.items.map { item ->
                PedidoRemoteDataSource.PedidoItemPayload(
                    id             = item.id,
                    productoId     = item.productoId,
                    nombre         = item.nombre,
                    precioUnitario = item.precioUnitario,
                    cantidad       = item.cantidad,
                    descuentoItem  = item.descuentoItem,
                    totalItem      = item.totalItem,
                    notes          = item.notes,
                )
            }

            // uploadPedido usa set/merge en Firestore → funciona tanto para create como update
            remoteDataSource.uploadPedido(pedido.id, pedidoPayload, itemPayloads)

            // Éxito → marcar SYNCED
            val syncedAt = System.currentTimeMillis()
            database.withTransaction {
                pedidoDao.markPedidoSynced(pedido.id, syncedAt)
                pedidoWithItems.items.forEach { item ->
                    pedidoItemDao.markItemSynced(item.id, syncedAt)
                }
            }
        } catch (e: Exception) {
            // Revertir a PENDING_UPDATE para reintento
            val revertAt = System.currentTimeMillis()
            database.withTransaction {
                pedidoDao.revertPedidoSyncingToPending(pedido.id, revertAt)
                pedidoWithItems.items.forEach { item ->
                    pedidoItemDao.revertItemSyncingToPending(item.id, revertAt)
                }
            }
            throw e
        }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private fun entityToDomain(entity: PedidoEntity): Pedido =
        Pedido(
            id              = entity.id,
            vendedorId      = entity.vendedorId,
            routeId         = entity.routeId,
            deliveryDate    = entity.deliveryDate,
            clienteId       = entity.clienteId,
            clienteSnapshot = ClienteSnapshot(
                nombre    = entity.clienteNombre,
                telefono  = entity.clienteTelefono,
                direccion = entity.clienteDireccion,
            ),
            items           = emptyList(), // ítems se cargan por separado
            subtotal        = entity.subtotal,
            descuentoGlobal = entity.descuentoGlobal,
            total           = entity.total,
            ivaAmount       = entity.ivaAmount,
            version         = entity.version,
            actualizadoPor  = entity.actualizadoPor,
            creadoEn        = entity.creadoEn,
            actualizadoEn   = entity.actualizadoEn,
        )

    private fun entityToSyncLabel(entity: PedidoEntity): SyncStatusLabel =
        when (entity.syncStatus) {
            SyncStatus.SYNCED                                              -> SyncStatusLabel.SYNCED
            SyncStatus.FAILED, SyncStatus.ERROR                           -> SyncStatusLabel.FAILED
            SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE,
            SyncStatus.PENDING, SyncStatus.SYNCING                        -> SyncStatusLabel.PENDING
            else                                                           -> SyncStatusLabel.UNKNOWN
        }

    override suspend fun recoverStuckSyncingPedidos() {        val revertAt = System.currentTimeMillis()
        val stuckPedidos = pedidoDao.getPedidosBySyncStatus(SyncStatus.SYNCING)
        if (stuckPedidos.isEmpty()) return

        database.withTransaction {
            stuckPedidos.forEach { pedido ->
                pedidoDao.revertPedidoSyncingToPendingCreate(pedido.id, revertAt)
                val stuckItems = pedidoItemDao.getItemsBySyncStatus(pedido.id, SyncStatus.SYNCING)
                stuckItems.forEach { item ->
                    pedidoItemDao.revertItemSyncingToPendingCreate(item.id, revertAt)
                }
            }
        }
    }

    override suspend fun deletePedido(pedidoId: String): Result<Unit> {
        return try {
            val entity = pedidoDao.getById(pedidoId)
                ?: return Result.Error(Failure.NotFound)

            when (entity.syncStatus) {
                SyncStatus.SYNCED -> {
                    // 1) Soft delete en Firestore (nunca borra físicamente el documento).
                    //    Si lanza excepción, el catch externo retorna NetworkError y Room
                    //    queda intacto — no hay estado intermedio huérfano.
                    val authUid = currentUserIdProvider.get()
                    remoteDataSource.softDeletePedido(
                        routeId = entity.routeId,
                        pedidoId = pedidoId,
                        orderKey = entity.orderKey,
                        deletedByUid = authUid,
                    )
                    // 2) Firestore confirmó → marcar isDeleted=true + PENDING_DELETE en Room
                    //    atómicamente con markPedidoDeleted (una sola query UPDATE).
                    pedidoDao.markPedidoDeleted(pedidoId, System.currentTimeMillis())
                }
                else -> {
                    // PENDING_CREATE / PENDING_UPDATE / FAILED / SYNCING:
                    // nunca llegó a Firestore (o falló) → borrar físicamente local.
                    pedidoDao.deleteById(pedidoId)
                }
            }

            // Borrar items locales en ambos casos (el FK CASCADE los borra automáticamente,
            // pero lo hacemos explícito para consistencia y por si la CASCADE falla).
            pedidoItemDao.deleteByPedidoId(pedidoId)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Failure.NetworkError)
        }
    }

    override suspend fun expireOldPedidos(
        thresholdDays: Long,
        graceDays: Long,
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val thresholdMs = thresholdDays * 24 * 60 * 60 * 1000L
            val graceMs     = graceDays     * 24 * 60 * 60 * 1000L

            val expired = pedidoDao.getExpiredPedidosWithItems(
                thresholdEpochMillis = now - thresholdMs
            )

            android.util.Log.d("PedidoExpire", "expireOldPedidos: found ${expired.size} expired pedidos")

            var successCount = 0
            var failureCount = 0

            for (relation in expired) {
                val entity = relation.pedido
                val pedidoId = entity.id

                try {
                    when (entity.syncStatus) {
                        // ── Solo expiramos pedidos DURABLEMENTE SINCRONIZADOS ──────
                        // Un pedido SYNCED ya está a salvo en Firestore; lo borramos
                        // localmente tras soft/hard-delete remoto (pipeline en 2 fases).
                        SyncStatus.SYNCED -> {
                            // Fase 1: marcar isDeleted=true en Firestore (primera vez)
                            // Fase 2: hard delete en Firestore si ya pasaron graceDays
                            //         desde que se marcó (usamos creadoEn + thresholdDays
                            //         + graceDays como proxy de cuándo se softDeleted).
                            val softDeleteEpoch = entity.creadoEn + thresholdMs
                            val readyForHardDelete = (now - softDeleteEpoch) >= graceMs

                            if (readyForHardDelete) {
                                // Fase 2 — hard delete físico en Firestore
                                remoteDataSource.hardDeletePedidoFromCloud(
                                    routeId  = entity.routeId,
                                    pedidoId = pedidoId,
                                )
                                android.util.Log.d("PedidoExpire", "expireOldPedidos: hard-delete cloud pedidoId=$pedidoId")
                            } else {
                                // Fase 1 — soft delete en Firestore
                                remoteDataSource.markPedidoExpiredInCloud(
                                    routeId  = entity.routeId,
                                    pedidoId = pedidoId,
                                )
                                android.util.Log.d("PedidoExpire", "expireOldPedidos: soft-delete cloud pedidoId=$pedidoId")
                            }

                            // Solo tras asegurar el remoto, borramos localmente.
                            pedidoItemDao.deleteByPedidoId(pedidoId)
                            pedidoDao.deleteById(pedidoId)
                            successCount++
                        }

                        // ── Cualquier estado con intención local SIN subir ─────────
                        // PENDING_CREATE / PENDING_UPDATE / SYNCING / FAILED / ERROR /
                        // CONFLICT: NUNCA borrar. Son ventas o ediciones que todavía no
                        // llegaron a Firestore; borrarlas localmente sería pérdida
                        // permanente (no hay copia remota). Las conservamos hasta que el
                        // sync las suba; una corrida posterior, ya en SYNCED, las expirará
                        // de forma segura.
                        else -> {
                            android.util.Log.w(
                                "PedidoExpire",
                                "expireOldPedidos: SKIP pedido no sincronizado pedidoId=$pedidoId status=${entity.syncStatus} (se conserva hasta subir)",
                            )
                        }
                    }
                } catch (e: Exception) {
                    failureCount++
                    android.util.Log.e("PedidoExpire", "expireOldPedidos: failed pedidoId=$pedidoId", e)
                    // Continuar con los demás pedidos
                }
            }

            android.util.Log.d("PedidoExpire", "expireOldPedidos: done success=$successCount failure=$failureCount")

            if (failureCount > 0 && successCount == 0 && expired.isNotEmpty()) {
                throw RuntimeException("All $failureCount pedido(s) failed to expire")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("PedidoExpire", "expireOldPedidos: fatal error", e)
            Result.Error(Failure.NetworkError)
        }
    }

    override suspend fun getReportData(params: ReportParams): ReportResult {
        val routeSales = pedidoDao.salesByRouteSince(params.sinceEpoch14).map { t ->
            RouteSalesData(routeId = t.routeId, totalVentas = t.totalVentas, pedidoCount = t.pedidoCount)
        }
        val dailySales = pedidoDao.salesByDaySince(params.sinceEpoch14).map { t ->
            DailySalesData(dateLabel = t.dateLabel, totalVentas = t.totalVentas, pedidoCount = t.pedidoCount)
        }
        val topProducts = pedidoDao.topProductsSince(params.sinceEpoch14, params.topLimit).map { t ->
            val product = productDao.getById(t.productoId)
            TopProductData(
                productoId = t.productoId,
                productName = t.productName,
                totalQty = t.totalQty,
                totalRevenue = t.totalRevenue,
                imageUrl = product?.imageUrl,
                imageLocalUri = product?.imageLocalUri,
            )
        }
        val bottomProducts = pedidoDao.bottomProductsSince(params.sinceEpoch14, params.topLimit).map { t ->
            val product = productDao.getById(t.productoId)
            TopProductData(
                productoId = t.productoId,
                productName = t.productName,
                totalQty = t.totalQty,
                totalRevenue = t.totalRevenue,
                imageUrl = product?.imageUrl,
                imageLocalUri = product?.imageLocalUri,
            )
        }
        val topClients = pedidoDao.topClientsSince(params.sinceEpoch7, params.topLimit).map { t ->
            TopClientData(clienteNombre = t.clienteNombre, totalSpent = t.totalSpent, orderCount = t.orderCount)
        }
        return ReportResult(
            totalVentas14 = pedidoDao.sumTotalSince(params.sinceEpoch14),
            totalPedidos14 = pedidoDao.countPedidosSince(params.sinceEpoch14),
            totalVentas7 = pedidoDao.sumTotalSince(params.sinceEpoch7),
            totalPedidos7 = pedidoDao.countPedidosSince(params.sinceEpoch7),
            routeSales = routeSales,
            dailySales = dailySales,
            topProducts = topProducts,
            bottomProducts = bottomProducts,
            topClients = topClients,
        )
    }
}
