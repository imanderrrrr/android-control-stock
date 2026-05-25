package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observa de forma reactiva los pedidos de "otros vendedores" para una ruta dada.
 *
 * - Devuelve un Flow que Room re-emite automáticamente cada vez que cambia
 *   cualquier order de esa ruta.
 * - El repositorio ya aplica el filtro Opción B (excluye pedidos del propio vendedor).
 * - La UI agrupa los resultados por ruta y muestra headers con botón de descarga.
 */
class ObserveOtherOrdersByRouteUseCase(
    private val repository: OrderRepository,
) {
    operator fun invoke(routeId: String): Flow<List<Order>> =
        repository.observeOrdersByRoute(routeId)
}

