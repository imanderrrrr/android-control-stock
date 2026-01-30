package com.are.distribuidora.data.local.mapper

import com.are.distribuidora.data.local.entity.SaleEntity
import com.are.distribuidora.data.local.entity.SaleItemEntity
import com.are.distribuidora.domain.model.Sale
import com.are.distribuidora.domain.model.SaleItem
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity

fun SaleEntity.toDomain(items: List<SaleItemEntity>): Sale = Sale(
    id = id,
    date = date,
    total = Money.of(total.toBigDecimal()),
    items = items.map { it.toDomain() },
)

fun Sale.toEntity(): SaleEntity = SaleEntity(
    id = id,
    date = date,
    total = total.amount.toDouble(),
)

fun SaleItemEntity.toDomain(): SaleItem = SaleItem(
    productId = ProductId.of(productId),
    quantity = Quantity.of(quantity),
    price = Money.of(price.toBigDecimal()),
)

fun SaleItem.toEntity(saleId: String, id: String): SaleItemEntity = SaleItemEntity(
    id = id,
    saleId = saleId,
    productId = productId.value,
    quantity = quantity.value,
    price = price.amount.toDouble(),
)
