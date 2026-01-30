package com.are.distribuidora.domain.product

/**
 * LEGACY (refactor pendiente):
 * El repositorio soberano de productos solo expone lectura (Flow<List<Product>>).
 */
@Deprecated(
    message = "Refactor pendiente: el dominio Product ya no expone escritura en este módulo",
    level = DeprecationLevel.ERROR,
)
class UpdateProductUseCase
