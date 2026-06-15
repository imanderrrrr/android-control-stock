package com.are.distribuidora.orders.domain.repository

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.model.EditOrderItemInput
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderItem
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    /**
     * Descarga headers desde Firestore (solo lectura) y hace upsert en Room.
     *
     * Reglas:
     * - NO descarga items
     * - status queda en ITEMS_PENDING
     */
    suspend fun fetchOrdersHeader(routeId: String, deliveryDate: String): Result<Unit>

    /**
     * Igual que [fetchOrdersHeader] pero sin filtrar por fecha.
     * Descarga TODOS los pedidos de una ruta.
     * Usado por "Otros Pedidos" para ver pedidos de todos los días.
     */
    suspend fun fetchAllOrdersHeader(routeId: String): Result<Unit>

    /**
     * Descarga items de UN pedido y hace commit atómico (staging -> final) solo si pasan validación.
     */
    suspend fun downloadOrderItems(orderId: String): Result<Unit>

    /**
     * Lectura local (fuente de verdad) para que la UI consuma más adelante.
     */
    suspend fun getOrdersByRouteAndDate(routeId: String, deliveryDate: String): List<Order>

    /**
     * Soft delete de un pedido:
     * 1) Marca isDeleted=true en Firestore (NO borra físicamente).
     * 2) Marca isDeleted=true localmente en Room.
     * 3) Elimina items locales (final + staging) para que el detalle no quede accesible offline.
     *
     * El pedido deja de aparecer en listas y no se descarga en futuros syncs.
     * Consistente con el patrón de productos/clientes.
     */
    suspend fun deleteOrder(routeId: String, orderId: String): Result<Unit>

    /**
     * Flow reactivo: emite la lista de Orders de una ruta cada vez que Room los actualiza.
     * Filtra en memoria los pedidos del propio vendedor (vendedorId == currentUid).
     * Excluye eliminados (isDeleted=true).
     */
    fun observeOrdersByRoute(routeId: String): Flow<List<Order>>

    /**
     * Flow reactivo filtrado por ruta Y fecha de entrega (YYYY-MM-DD).
     * Aplica Opción B (excluye pedidos propios) en memoria.
     * Excluye eliminados.
     */
    fun observeOrdersByRouteAndDate(routeId: String, deliveryDate: String): Flow<List<Order>>

    /**
     * Obtiene la cabecera de un pedido por orderId (consulta directa, no reactiva).
     * Devuelve null si no existe o está eliminado.
     */
    suspend fun getOrderById(orderId: String): Order?

    /**
     * Obtiene los ítems ya descargados de un pedido desde Room.
     * Devuelve lista vacía si no hay ítems o el pedido no existe.
     */
    suspend fun getItemsByOrderId(orderId: String): List<OrderItem>

    /**
     * Persiste una EDICIÓN de ítems de un pedido (propio o ajeno) en Room y la marca como
     * pendiente de subir (pendingUpload=1). Reemplaza el set de ítems, recalcula el total y
     * deja el header COMPLETED. NO cambia el dueño (vendedorId/sellerName).
     *
     * Acepta ítems personalizados (productId `custom_<UUID>` sin producto en catálogo).
     * Requiere al menos un ítem y cantidades > 0.
     *
     * La subida remota la dispara el llamador encolando el worker de subida.
     */
    suspend fun editOrderItems(orderId: String, items: List<EditOrderItemInput>): Result<Unit>

    /**
     * Sube a Firestore todas las ediciones locales pendientes (pendingUpload=1) y limpia el
     * flag al confirmar cada una. Preserva vendedorId/sellerName. Idempotente y reintentable
     * (lo invoca el worker de subida de pedidos). Devuelve error si alguna subida falló para
     * que el worker reintente.
     */
    suspend fun uploadPendingOrders(): Result<Unit>
}
