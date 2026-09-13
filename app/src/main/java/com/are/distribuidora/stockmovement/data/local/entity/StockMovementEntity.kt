package com.are.distribuidora.stockmovement.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement

/**
 * Tabla `stock_movements`: libro de movimientos de inventario (espejo de la colección
 * Firestore `stock_movements/{id}`).
 *
 * Inmutable: solo se inserta. `syncStatus` es local (PENDING_CREATE → SYNCING → SYNCED).
 * `type` y `reason` se guardan como texto (nombre del enum) para que el schema sea legible.
 */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["orderId"]),
        Index(value = ["syncStatus"]),
    ],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val type: String,
    val quantity: Int,
    val reason: String,
    val orderId: String?,
    val note: String?,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncedAt: Long? = null,
) {
    fun toDomain(): StockMovement = StockMovement(
        id = id,
        productId = productId,
        productName = productName,
        type = MovementType.valueOf(type),
        quantity = quantity,
        reason = MovementReason.valueOf(reason),
        orderId = orderId,
        note = note,
        createdBy = createdBy,
        createdByName = createdByName,
        createdAt = createdAt,
    )

    /** Efecto con signo sobre el stock local. */
    val signedQuantity: Int get() = if (type == MovementType.ENTRADA.name) quantity else -quantity

    companion object {
        fun fromDomain(m: StockMovement, syncStatus: SyncStatus): StockMovementEntity = StockMovementEntity(
            id = m.id,
            productId = m.productId,
            productName = m.productName,
            type = m.type.name,
            quantity = m.quantity,
            reason = m.reason.name,
            orderId = m.orderId,
            note = m.note,
            createdBy = m.createdBy,
            createdByName = m.createdByName,
            createdAt = m.createdAt,
            syncStatus = syncStatus,
        )
    }
}
