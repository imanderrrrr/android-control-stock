package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Sale
import com.are.distribuidora.domain.model.SaleItem

/**
 * Crea una venta en memoria aplicando reglas mínimas y devuelve el modelo de dominio.
 *
 * Persistencia/sync no es responsabilidad del dominio en este refactor.
 */
class CreateSaleUseCase {

    operator fun invoke(
        id: String,
        date: Long,
        items: List<SaleItem>,
    ): Sale {
        require(id.isNotBlank()) { "id requerido" }
        require(date > 0L) { "date inválido" }
        require(items.isNotEmpty()) { "La venta debe tener al menos un item" }

        // VOs ensure valid state (quantity > 0, price >= 0, productId not blank)
        // Explicit checks removed as they are redundant with VO rules, 
        // EXCEPT if we want to ensure price > 0 (strictly positive, as Money allows 0).
        items.forEach { item ->
             require(item.price.amount.toDouble() > 0.0) { "price debe ser > 0" }
        }

        val total = items
            .map { it.price * it.quantity.value }
            .fold(com.are.distribuidora.domain.valueobject.Money.zero()) { acc, m -> acc + m }
            
        require(total.amount.toDouble() > 0.0) { "total debe ser > 0" }

        return Sale(
            id = id,
            date = date,
            total = total,
            items = items,
        )
    }
}
