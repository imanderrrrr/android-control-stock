package com.are.distribuidora.route.data.remote

import com.are.distribuidora.route.data.remote.dto.RouteDto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Implementación Firestore para Rutas.
 *
 * Colección: routes
 * Documento: routeId
 */
class FirestoreRouteRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : RouteRemoteDataSource {

    private val routesCollection = firestore.collection("routes")
    private val clientsCollection = firestore.collection("clients")

    override suspend fun upsertRoute(route: RouteDto) {
        val payload = hashMapOf(
            "id" to route.id,
            "name" to route.name,
            "deliveryDay" to route.deliveryDay,
            "isDeleted" to route.isDeleted,
            "createdAt" to route.createdAt,
            "updatedAt" to route.updatedAt,
        )
        routesCollection.document(route.id).set(payload).await()
    }

    override suspend fun fetchRoutes(limit: Int): List<RouteDto> {
        val snap = routesCollection.limit(limit.toLong()).get().await()
        val docs = snap.documents
        if (docs.isEmpty()) {
            return emptyList()
        }

        return docs.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name")?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val deliveryDayAny = doc.get("deliveryDay")
            val deliveryDay = when (deliveryDayAny) {
                is Number -> deliveryDayAny.toInt()
                is String -> deliveryDayAny.toIntOrNull() ?: 0
                else -> 0
            }
            val createdAt = doc.getLong("createdAt") ?: 0L
            val updatedAt = doc.getLong("updatedAt") ?: createdAt
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            RouteDto(
                id = id,
                name = name,
                deliveryDay = deliveryDay,
                synced = true,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isDeleted = isDeleted,
            )
        }
    }

    override suspend fun updateRouteDeliveryDay(routeId: String, deliveryDay: Int, updatedAt: Long) {
        routesCollection.document(routeId).update(
            mapOf(
                "deliveryDay" to deliveryDay,
                "updatedAt" to updatedAt,
            )
        ).await()
    }

    override suspend fun assignClientRoute(clientId: String, routeId: String?) {
        clientsCollection.document(clientId).update(
            mapOf("routeId" to routeId)
        ).await()
    }

    override suspend fun softDeleteRoute(routeId: String, updatedAt: Long) {
        routesCollection.document(routeId).update(
            mapOf(
                "isDeleted" to true,
                "updatedAt" to updatedAt,
            )
        ).await()
    }

    override suspend fun deleteRoute(routeId: String) {
        routesCollection.document(routeId).delete().await()
    }
}
