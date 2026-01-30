package com.are.distribuidora.client.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    indices = [Index(value = ["routeId"], name = "index_clients_routeId")]
)
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String?,
    val createdAt: Long,
    /**
     * Relación 1:N: una Ruta tiene muchos clientes, pero el cliente solo tiene una ruta.
     * Nullable para permitir clientes sin ruta asignada.
     */
    val routeId: String? = null,
)
