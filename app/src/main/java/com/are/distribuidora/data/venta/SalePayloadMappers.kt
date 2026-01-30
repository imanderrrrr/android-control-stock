package com.are.distribuidora.data.venta

import com.are.distribuidora.domain.model.Sale

/**
 * Mappers remoto -> dominio para ventas.
 *
 * En este refactor, el modelo soberano es `com.are.distribuidora.domain.model.Sale`.
 *
 * Este archivo existía como legacy; lo mantenemos como capa de compatibilidad para que cualquier
 * consumidor de infraestructura tenga una función clara para obtener el modelo de dominio.
 */
internal fun SaleDocument.toSaleDomain(
    id: String,
    date: Long,
): Sale = toDomain(id = id, date = date)
