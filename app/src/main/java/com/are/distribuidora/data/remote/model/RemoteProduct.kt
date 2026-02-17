package com.are.distribuidora.data.remote.model

data class RemoteProduct(
    val id: String,
    val name: String,
    val description: String?,
    val category: String?,
    val price: Double?,
    val imageUrl: String?,
    val barcode: String?,
    val stock: Int?,
    val comprometido: Int?,
    val isActive: Boolean?,
    val isDeleted: Boolean?,
    val createdRemoteAt: Long?,
    val updatedRemoteAt: Long?
)
