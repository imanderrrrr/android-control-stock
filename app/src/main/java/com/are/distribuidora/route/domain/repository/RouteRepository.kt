package com.are.distribuidora.route.domain.repository

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.model.Route

interface RouteRepository {
    suspend fun create(route: Route): Result<Unit>
    suspend fun getRoutes(limit: Int = 200): Result<List<Route>>
    suspend fun getRouteById(id: String): Result<Route?>

    /** Actualiza el día de entrega de una ruta. */
    suspend fun updateDeliveryDay(routeId: String, deliveryDay: Int): Result<Unit>

    /** Asigna un cliente a una ruta (o null para quitarle la ruta). */
    suspend fun assignClientToRoute(clientId: String, routeId: String?): Result<Unit>
}
