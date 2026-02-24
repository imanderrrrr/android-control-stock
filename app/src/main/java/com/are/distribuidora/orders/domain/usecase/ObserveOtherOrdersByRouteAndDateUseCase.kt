package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observa de forma reactiva los pedidos de "otros vendedores" para una ruta y fecha dadas.
 *
 * - Devuelve un Flow que Room re-emite automáticamente cada vez que cambia
 *   cualquier order de esa ruta+fecha.
 * - El repositorio aplica el filtro Opción B (excluye pedidos del propio vendedor).
 * - La UI agrupa los resultados por ruta. Cambiar [deliveryDate] requiere re-suscripción
 *   (gestionada en el ViewModel con flatMapLatest).
 */
class ObserveOtherOrdersByRouteAndDateUseCase(
    private val repository: OrderRepository,
) {
    operator fun invoke(routeId: String, deliveryDate: String): Flow<List<Order>> =
        repository.observeOrdersByRouteAndDate(routeId = routeId, deliveryDate = deliveryDate)
}

