package com.are.distribuidora.data.repository.hybrid

/**
 * LEGACY (refactor): sincronización híbrida de ventas fue retirada.
 *
 * El concepto Sale ahora tiene un único modelo soberano en:
 * - com.are.distribuidora.domain.model.Sale
 */
@Deprecated(
    message = "Refactor pendiente: reintroducir sync de ventas con el modelo soberano",
    level = DeprecationLevel.ERROR,
)
class HybridSaleRepository
