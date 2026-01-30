package com.are.distribuidora.domain.product

/**
 * LEGACY (refactor pendiente):
 * El repositorio soberano de productos solo expone lectura (Flow<List<Product>>).
 *
 * Este caso de uso se mantiene compilable pero deshabilitado para no violar las reglas de oro.
 */
@Deprecated(
    message = "Refactor pendiente: el dominio Product ya no expone escritura en este módulo",
    level = DeprecationLevel.ERROR,
)
class CreateProductUseCase
