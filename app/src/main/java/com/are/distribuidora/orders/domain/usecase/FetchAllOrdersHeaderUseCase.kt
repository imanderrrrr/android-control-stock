package com.are.distribuidora.orders.domain.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.orders.domain.repository.OrderRepository

/**
 * Descarga los headers de TODOS los pedidos de una ruta desde Firestore,
 * sin filtrar por fecha de entrega.
 *
 * Usado por "Otros Pedidos" para mostrar pedidos de todos los días
 * de otros vendedores en la misma ruta.
 */
class FetchAllOrdersHeaderUseCase(
    private val repository: OrderRepository,
) {
    suspend fun execute(routeId: String): Result<Unit> =
        repository.fetchAllOrdersHeader(routeId = routeId)
}

