package com.are.distribuidora.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cola de subida offline-first para imágenes de productos.
 *
 * Cada vez que el usuario selecciona/toma una imagen para un producto,
 * se crea un registro aquí. El Worker se encarga de subir a Firebase Storage
 * y actualizar Firestore con la URL remota.
 */
@Entity(tableName = "pending_uploads")
data class PendingUploadEntity(
    @PrimaryKey val id: String,
    /** Tipo de entidad, e.g. "PRODUCT" */
    val entityType: String = "PRODUCT",
    /** ID del producto asociado */
    val entityId: String,
    /** URI local del archivo (absolute path) */
    val localUri: String,
    /** Ruta determinística en Firebase Storage, e.g. "products/{productId}/main.jpg" */
    val storagePath: String,
    /** Estado: PENDING, UPLOADING, DONE, FAILED */
    val state: String = "PENDING",
    /** Número de intentos realizados */
    val attemptCount: Int = 0,
    /** Último error registrado */
    val lastError: String? = null,
    /** Timestamp de creación */
    val createdAt: Long
)

