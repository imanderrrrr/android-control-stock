package com.are.distribuidora.data.local.entity

import androidx.room.*

/**
 * Tabla auxiliar para almacenar el snapshot remoto de un producto en conflicto.
 *
 * Cuando ocurre un conflicto (Local PENDING_UPDATE vs Remote NEWER):
 * 1. El producto en `products` se marca como [com.are.distribuidora.data.local.SyncStatus.CONFLICT].
 * 2. Los datos remotos que causaron el conflicto se guardan aquí.
 * 3. La UI debe mostrar ambas versiones y permitir al usuario decidir (Merge/Discard/Overwrite).
 */
@Entity(
    tableName = "product_conflicts",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class ProductConflictEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val productId: String,

    /**
     * JSON completo del producto remoto.
     * Serializamos todo el objeto remoto para máxima flexibilidad en la resolución futura.
     */
    val remoteJson: String,

    /**
     * Timestamp del registro remoto (updatedAt).
     */
    val remoteUpdatedAt: Long,

    /**
     * Fecha/hora local en que se detectó el conflicto.
     */
    val conflictDetectedAt: Long = System.currentTimeMillis()
)
