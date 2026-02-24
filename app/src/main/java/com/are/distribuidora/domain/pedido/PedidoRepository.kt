package com.are.distribuidora.domain.pedido

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.domain.pedido.model.EditPedidoParams
import kotlinx.coroutines.flow.Flow

interface PedidoRepository {
    suspend fun createPedido(params: CreatePedidoParams): Result<String>

    suspend fun listPedidosByCliente(clienteId: String): Result<List<Pedido>>

    suspend fun findActivePedidoByOrderKey(orderKey: String): String?
    /**
     * Regla de negocio: 1 pedido por cliente por dia de creacion.
     *
     * Devuelve el ID del primer pedido activo que encuentre para [clienteId]
     * creado el mismo dia que [creationEpochMs], o null si no existe ninguno.
     *
     * "Mismo dia" se evalua en la zona horaria local del dispositivo.
     * El worker de expiracion a 14 dias es independiente de esta regla.
     */
    suspend fun findActivePedidoByClienteAndDay(
        clienteId: String,
        creationEpochMs: Long,
    ): String?

    /**
     * Devuelve todos los pedidos locales con sus Ã­tems (one-shot).
     */
    suspend fun getAllPedidosWithItems(): List<PedidoWithItems>

    /**
     * VersiÃ³n reactiva: emite la lista completa cada vez que cambia cualquier
     * pedido en Room (syncStatus, datos, etc.).  Nunca completa salvo cancelaciÃ³n.
     */
    fun observeAllPedidosWithItems(): Flow<List<PedidoWithItems>>

    /**
     * VersiÃ³n reactiva filtrada por fecha de entrega "YYYY-MM-DD".
     * Si [deliveryDate] es vacÃ­o, devuelve todos los pedidos (sin filtro).
     */
    fun observeAllPedidosWithItemsByDate(deliveryDate: String): Flow<List<PedidoWithItems>>

    /**
     * Observa en tiempo real un Ãºnico pedido con sus Ã­tems activos.
     * Usado por la pantalla de ediciÃ³n para reflejar cambios inmediatamente.
     */
    fun observePedidoWithItems(pedidoId: String): Flow<PedidoWithItems?>

    /**
     * Obtiene pedidos pendientes de sync (PENDING_CREATE / SYNCING) junto con sus Ã­tems.
     * Usado por el worker de sincronizaciÃ³n para creaciones nuevas.
     */
    suspend fun getPendingPedidosForSync(limit: Int): List<PedidoWithItems>

    /**
     * Obtiene pedidos en estado PENDING_UPDATE junto con sus Ã­tems activos.
     * Usado por el worker de sincronizaciÃ³n para actualizaciones de pedidos editados.
     */
    suspend fun getPendingUpdatePedidosForSync(limit: Int): List<PedidoWithItems>

    /**
     * Edita un pedido existente de forma atÃ³mica:
     * - Upsert de Ã­tems modificados/nuevos.
     * - Soft delete de Ã­tems eliminados (isDeleted=true).
     * - Recalcula subtotal/total.
     * - Marca el pedido como PENDING_UPDATE.
     *
     * Solo permitido para pedidos propios (vendedorId == usuario actual).
     */
    suspend fun editPedido(params: EditPedidoParams): Result<Unit>

    /**
     * Sube un pedido a Firestore de forma atÃ³mica y marca como SYNCED en Room.
     * Si falla, revierte a PENDING_CREATE.
     *
     * @throws Exception si la subida falla (para que el caller decida reintentar).
     */
    suspend fun uploadAndMarkSynced(pedidoWithItems: PedidoWithItems)

    /**
     * Actualiza un pedido editado en Firestore (PENDING_UPDATE â†’ SYNCED).
     * Si falla, revierte a PENDING_UPDATE.
     *
     * @throws Exception si la actualizaciÃ³n falla.
     */
    suspend fun updateAndMarkSynced(pedidoWithItems: PedidoWithItems)

    /**
     * Revierte pedidos (y sus Ã­tems) que quedaron atascados en SYNCING a PENDING_CREATE.
     * Debe llamarse al arrancar la app para recuperar pedidos que no completaron la subida
     * debido a un crash o kill del proceso antes de que se confirmara SYNCED en Room.
     */
    suspend fun recoverStuckSyncingPedidos()

    /**
     * Elimina un pedido propio (soft delete).
     * - Si syncStatus == SYNCED â†’ marca isDeleted en Firestore + PENDING_DELETE local.
     * - Si syncStatus == PENDING_CREATE â†’ borra fÃ­sicamente de Room (nunca llegÃ³ a Firestore).
     * - Elimina items locales en ambos casos.
     */
    suspend fun deletePedido(pedidoId: String): Result<Unit>

    /**
     * Expira pedidos con antigÃ¼edad â‰¥ [thresholdDays] dÃ­as:
     *
     * Para cada pedido expirado:
     *  - Si estaba SYNCED â†’ marca `isDeleted=true` en Firestore (fase 1) y borra localmente.
     *  - Si ya lleva â‰¥ [graceDays] dÃ­as marcado en Firestore â†’ hard delete en Firestore (fase 2).
     *  - Si era PENDING_CREATE (nunca subido) â†’ solo borra fÃ­sicamente en Room.
     *
     * @param thresholdDays  AntigÃ¼edad mÃ­nima para expirar (default 14).
     * @param graceDays      DÃ­as de gracia en nube antes del hard delete (default 2).
     */
    suspend fun expireOldPedidos(
        thresholdDays: Long = 14L,
        graceDays: Long = 2L,
    ): Result<Unit>
}
