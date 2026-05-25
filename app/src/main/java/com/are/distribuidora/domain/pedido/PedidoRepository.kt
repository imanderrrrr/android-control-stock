package com.are.distribuidora.domain.pedido
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import com.are.distribuidora.domain.pedido.model.ReportParams
import com.are.distribuidora.domain.pedido.model.ReportResult
import kotlinx.coroutines.flow.Flow
interface PedidoRepository {
    suspend fun createPedido(params: CreatePedidoParams): Result<String>
    suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>>
    suspend fun findActivePedidoByOrderKey(orderKey: String): String?
    suspend fun findActivePedidoByClienteAndDay(
        clienteId: String,
        creationEpochMs: Long,
    ): String?
    suspend fun getAllPedidosWithItems(): List<PedidoWithItems>
    fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>>
    fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>>
    fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?>
    suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems>
    suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems>
    suspend fun editPedido(params: EditPedidoParams): Result<Unit>
    suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems)
    suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems)
    suspend fun recoverStuckSyncingPedidos()
    suspend fun deletePedido(pedidoId: String): Result<Unit>
    suspend fun expireOldPedidos(
        thresholdDays: Long = 14L,
        graceDays: Long = 2L,
    ): Result<Unit>
    /** Devuelve los datos de reporte de ventas para los periodos indicados en [params]. */
    suspend fun getReportData(params: ReportParams): ReportResult
}
