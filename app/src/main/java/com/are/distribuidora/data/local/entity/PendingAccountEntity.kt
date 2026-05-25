package com.are.distribuidora.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.are.distribuidora.client.data.local.entity.ClientEntity
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.route.data.local.entity.RouteEntity

/**
 * Cuenta pendiente de cobro.
 *
 * Relacionada a una ruta y un cliente de esa ruta.
 * Contiene monto, foto de factura, y fecha límite de pago.
 */
@Entity(
    tableName = "pending_accounts",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["routeId"]),
        Index(value = ["clientId"]),
        Index(value = ["dueDateMillis"]),
    ],
)
data class PendingAccountEntity(
    @PrimaryKey val id: String,
    val routeId: String,
    val clientId: String,
    val clientName: String,
    val routeName: String,
    /** Monto total pendiente en centavos (para evitar floating-point). Se muestra en Q. */
    val amountCents: Long,
    /** URI local de la foto de la factura. */
    val invoicePhotoUri: String?,
    /** URL remota (https://...) de la foto en Firebase Storage. Se llena tras subir. */
    val invoiceRemoteUrl: String? = null,
    /** Fecha límite de pago (epoch millis, inicio del día). */
    val dueDateMillis: Long,
    /** true si ya se pagó / se resolvió. */
    val isPaid: Boolean = false,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,

    // ── Sync fields ──────────────────────────────────────────────────────
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,

    // ── Activity log fields ──────────────────────────────────────────────
    /** Email del usuario que pagó/eliminó la cuenta. */
    val resolvedBy: String? = null,
    /** Timestamp de cuándo se pagó/eliminó. */
    val resolvedAt: Long? = null,
    /** "PAID" o "DELETED". null si aún activa. */
    val resolvedAction: String? = null,
)


