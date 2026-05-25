package com.are.distribuidora.pedido.presentation.catalog

import com.are.distribuidora.domain.model.Product

/**
 * Modelo de presentación para el catálogo: empaqueta un [Product] junto
 * con la cantidad actual en el carrito.
 *
 * Existe **solo en la capa de presentation** — el dominio NO conoce el
 * carrito. Esto nos permite alimentar el adapter con una única fuente de
 * verdad atómica (productos + cantidades en el mismo PagingData), eliminando
 * la race condition que causaba el crash de RecyclerView
 * "Inconsistency detected. Invalid item position …".
 *
 * Ver `OrderCatalogViewModel.productsWithQty` para el combine que lo produce
 * y `OrderCatalogAdapter` para el uso.
 */
data class ProductWithQty(
    val product: Product,
    val qty: Int,
) {
    val id: String get() = product.id.value
}
