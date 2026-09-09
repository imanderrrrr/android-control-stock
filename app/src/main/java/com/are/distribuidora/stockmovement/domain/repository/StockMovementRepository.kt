package com.are.distribuidora.stockmovement.domain.repository

import com.are.distribuidora.stockmovement.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow

/**
 * Libro de movimientos de inventario (offline-first).
 *
 * Los movimientos AUTOMÁTICOS (pedidos) los escribe el repositorio de pedidos en la misma
 * transacción Room que el pedido; este contrato cubre los vales manuales y la lectura.
 */
interface StockMovementRepository {

    /**
     * Registra un vale manual: inserta el movimiento como pendiente de subir y mueve el stock
     * local del producto en la misma transacción. Dispara la sincronización.
     */
    suspend fun recordVoucher(movement: StockMovement)

    /** Historial de un producto, del más reciente al más antiguo. */
    fun observeByProduct(productId: String, limit: Int = 100): Flow<List<StockMovement>>

    /**
     * Sube a Firestore los movimientos pendientes que NO viajan con un pedido (vales manuales y
     * automáticos huérfanos). Cada uno se escribe con create + incremento atómico del stock.
     */
    suspend fun uploadPending()
}
