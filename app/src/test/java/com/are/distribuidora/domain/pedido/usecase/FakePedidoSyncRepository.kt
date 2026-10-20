package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.ReportParams
import com.are.distribuidora.domain.pedido.model.ReportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Cola de subida de pedidos en memoria con la misma semántica que [PedidoRepositoryImpl]:
 *
 * - `getPendingPedidosForSync` devuelve PENDING_CREATE **y SYNCING**, en orden de llegada.
 * - `getPendingUpdatePedidosForSync` devuelve PENDING_UPDATE.
 * - Subir marca SYNCING; si la subida lanza, revierte a su estado pendiente y relanza.
 * - Marcar SYNCED es condicional a seguir en SYNCING (como `markPedidoSyncedInternal`): una
 *   edición durante la subida deja el pedido en PENDING_UPDATE con versión nueva.
 *
 * [failWith] fija un error permanente por pedido; [onUpload] se ejecuta en mitad de cada subida
 * (con el pedido ya en SYNCING) para simular confirmaciones, ediciones o ejecuciones concurrentes.
 */
class FakePedidoSyncRepository : PedidoRepository {

    enum class State { PENDING_CREATE, PENDING_UPDATE, SYNCING, SYNCED }

    val states = linkedMapOf<String, State>()
    val uploads = mutableListOf<String>()
    val versions = mutableMapOf<String, Int>()
    val failWith = mutableMapOf<String, Exception>()
    var onUpload: suspend (pedidoId: String) -> Unit = {}

    fun addPendingCreate(vararg ids: String) = ids.forEach { states[it] = State.PENDING_CREATE }
    fun addPendingUpdate(vararg ids: String) = ids.forEach { states[it] = State.PENDING_UPDATE }
    fun idsIn(state: State): List<String> = states.filterValues { it == state }.keys.toList()

    /** Edición local: sube la versión y deja el pedido pendiente de actualizar. */
    fun edit(id: String) {
        versions[id] = (versions[id] ?: 1) + 1
        states[id] = State.PENDING_UPDATE
    }

    override suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems> =
        states.filterValues { it == State.PENDING_CREATE || it == State.SYNCING }
            .keys.take(limit).map(::pedidoWithItems)

    override suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems> =
        idsIn(State.PENDING_UPDATE).take(limit).map(::pedidoWithItems)

    override suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems) =
        upload("create", pedidoWithItems.pedido.id, revertTo = State.PENDING_CREATE)

    override suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems) =
        upload("update", pedidoWithItems.pedido.id, revertTo = State.PENDING_UPDATE)

    private suspend fun upload(kind: String, id: String, revertTo: State) {
        uploads += "$kind:$id"
        if (uploads.size > 1_000) throw AssertionError("Bucle infinito: más de 1000 subidas en una ejecución")
        states[id] = State.SYNCING
        try {
            onUpload(id)
            failWith[id]?.let { throw it }
            if (states[id] == State.SYNCING) states[id] = State.SYNCED
        } catch (e: Exception) {
            states[id] = revertTo
            throw e
        }
    }

    private fun pedidoWithItems(id: String): PedidoWithItems {
        val pedido = Pedido(
            id = id,
            vendedorId = "vend-1",
            routeId = "ruta-1",
            deliveryDate = "2026-10-20",
            clienteId = "cliente-1",
            clienteSnapshot = ClienteSnapshot(nombre = "Cliente", telefono = null, direccion = null),
            items = emptyList(),
            subtotal = 0.0,
            descuentoGlobal = 0.0,
            total = 0.0,
            version = versions[id] ?: 1,
            actualizadoPor = "vend-1",
            creadoEn = 0L,
            actualizadoEn = 0L,
        )
        return PedidoWithItems(pedido = pedido, items = emptyList())
    }

    // ── Resto del contrato: no interviene en la subida ───────────────────────

    override suspend fun createPedido(params: CreatePedidoParams): Result<String> = error("no usado")
    override suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>> = error("no usado")
    override suspend fun findActivePedidoByOrderKey(orderKey: String): String? = error("no usado")
    override suspend fun findActivePedidoByClienteAndDay(clienteId: String, creationEpochMs: Long): String? = error("no usado")
    override suspend fun getAllPedidosWithItems(): List<PedidoWithItems> = error("no usado")
    override fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>> = emptyFlow()
    override fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>> = emptyFlow()
    override fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?> = emptyFlow()
    override suspend fun editPedido(params: EditPedidoParams): Result<Unit> = error("no usado")
    override suspend fun recoverStuckSyncingPedidos() = error("no usado")
    override suspend fun deletePedido(pedidoId: String): Result<Unit> = error("no usado")
    override suspend fun expireOldPedidos(thresholdDays: Long, graceDays: Long): Result<Unit> = error("no usado")
    override suspend fun getReportData(params: ReportParams): ReportResult = error("no usado")
}

class FakeConnectivityChecker(var online: Boolean = true) : ConnectivityChecker {
    override suspend fun isOnline(): Boolean = online
}

object NoopLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
