package com.are.distribuidora.domain.pedido

/**
 * Modelo de dominio que agrupa un pedido con sus ítems,
 * pensado para el flujo de sincronización offline→online.
 *
 * [syncStatusLabel] es metadata de infraestructura local (estado Room) expuesta
 * para que la UI muestre el estado de sync sin introducir dependencias de Room
 * en la capa de presentación. No es lógica de negocio.
 */
data class PedidoWithItems(
    val pedido: Pedido,
    val items: List<PedidoItem>,
    val syncStatusLabel: SyncStatusLabel = SyncStatusLabel.UNKNOWN,
)

enum class SyncStatusLabel {
    PENDING,   // PENDING_CREATE / PENDING_UPDATE / SYNCING
    SYNCED,    // SYNCED
    FAILED,    // FAILED / ERROR
    UNKNOWN,
}

