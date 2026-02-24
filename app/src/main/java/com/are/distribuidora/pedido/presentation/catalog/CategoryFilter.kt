package com.are.distribuidora.pedido.presentation.catalog

/**
 * Filtro de categoría para el catálogo de pedidos.
 *
 * Vive únicamente en presentation: NO toca dominio ni data.
 * La comparación se normaliza con trim().lowercase() para tolerar
 * variaciones de mayúsculas/espacios en BD.
 */
enum class CategoryFilter(
    /** Etiqueta visible en el chip. */
    val label: String,
    /** Valor normalizado para comparar con Product.category.
     *  null => sin filtro (mostrar todos). */
    val normalizedValue: String?,
) {
    TODOS("Todos", null),
    ALIMENTOS("Alimentos", "alimentos"),
    BEBIDAS("Bebidas", "bebidas"),
    HOGAR_Y_LIMPIEZA("Hogar y limpieza", "hogar y limpieza"),
    SNACKS("Snacks", "snacks"),
    HIGIENE("Higiene", "higiene"),
    VARIOS("Varios", "varios");

    /** Devuelve true si el producto pertenece a esta categoría (o si el filtro es TODOS). */
    fun matches(productCategory: String?): Boolean {
        if (normalizedValue == null) return true          // TODOS
        val normalized = productCategory?.trim()?.lowercase() ?: return false
        return normalized == normalizedValue
    }
}

