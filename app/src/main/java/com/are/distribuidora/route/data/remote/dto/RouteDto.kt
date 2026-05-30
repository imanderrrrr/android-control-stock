package com.are.distribuidora.route.data.remote.dto

data class RouteDto(
    val id: String,
    val name: String,
    val deliveryDay: Int,
    val synced: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
