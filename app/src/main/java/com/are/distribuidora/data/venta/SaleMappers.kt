package com.are.distribuidora.data.venta

import com.are.distribuidora.domain.model.Sale
import com.are.distribuidora.domain.model.SaleItem

/**
 * Mappers de infraestructura (Firestore) para el modelo soberano de dominio.
 *
 * Nota: este paquete (`data.venta`) es infraestructura. Aquí sí es válido hablar de "documentos".
 */
internal fun Sale.toDocument(
    clientName: String,
    clientId: String? = null,
    address: String? = null,
): SaleDocument = SaleDocument(
    client = SaleClientDocument(
        name = clientName,
        clientId = clientId,
        address = address,
    ),
    items = items.map { it.toDocument() },
)

internal fun SaleItem.toDocument(): SaleItemDocument = SaleItemDocument(
    // En dominio el id es ProductId; Firestore legacy lo modelaba como Long.
    // Convertimos de forma segura: si no es numérico, guardamos 0 y mantenemos el valor en el dominio.
    productId = productId.value.toLongOrNull() ?: 0L,
    quantity = quantity.value,
    unitPrice = price.amount.toDouble(),
)

internal fun SaleDocument.toDomain(
    id: String,
    date: Long,
): Sale {
    val domainItems = items.map { it.toDomain() }
    // Calculating total using Money arithmetic
    val total = domainItems
        .map { it.price * it.quantity.value }
        .fold(com.are.distribuidora.domain.valueobject.Money.zero()) { acc, m -> acc + m }

    return Sale(
        id = id,
        date = date,
        total = total,
        items = domainItems,
    )
}

internal fun SaleItemDocument.toDomain(): SaleItem = SaleItem(
    productId = com.are.distribuidora.domain.valueobject.ProductId.of(productId.toString()),
    quantity = com.are.distribuidora.domain.valueobject.Quantity.of(quantity),
    price = com.are.distribuidora.domain.valueobject.Money.of(unitPrice.toBigDecimal()),
)
