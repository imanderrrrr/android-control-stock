package com.are.distribuidora.data.local.mapper

import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity

fun ProductEntity.toDomain(): Product = Product(
    id = ProductId.of(id),
    name = name,
    price = Money.of(price.toBigDecimal()),
    stock = Quantity.of(stock),
)

fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id.value,
    name = name,
    price = price.amount.toDouble(),
    stock = stock.value,
)
