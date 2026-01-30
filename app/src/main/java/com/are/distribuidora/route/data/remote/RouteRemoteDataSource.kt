package com.are.distribuidora.route.data.remote

import com.are.distribuidora.route.data.remote.dto.RouteDto

interface RouteRemoteDataSource {
    suspend fun upsertRoute(route: RouteDto)
    suspend fun fetchRoutes(limit: Int = 200): List<RouteDto>
    suspend fun updateRouteDeliveryDay(routeId: String, deliveryDay: Int, updatedAt: Long)
    suspend fun assignClientRoute(clientId: String, routeId: String?)
}
