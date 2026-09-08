package com.are.distribuidora.stockmovement.data.remote

import com.are.distribuidora.stockmovement.domain.model.StockMovement

/**
 * Subida de movimientos que NO viajan con un pedido (vales manuales y automáticos huérfanos).
 *
 * Contrato de idempotencia: cada movimiento se escribe SOLO si no existe (create). El stock del
 * producto se mueve con un incremento atómico en la misma transacción. Un movimiento que ya
 * existía en el servidor se considera subido (no vuelve a incrementar).
 */
interface StockMovementRemoteDataSource {
    /**
     * @return ids de los movimientos que quedaron persistidos en el servidor (nuevos o ya existentes).
     */
    suspend fun uploadMovements(movements: List<StockMovement>): List<String>
}
