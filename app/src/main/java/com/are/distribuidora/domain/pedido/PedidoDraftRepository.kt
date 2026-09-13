package com.are.distribuidora.domain.pedido

import com.are.distribuidora.domain.pedido.model.PedidoDraft

/**
 * Persistencia local del borrador del pedido en curso.
 *
 * Contrato deliberadamente mínimo: un borrador por vendedor, que se guarda entero
 * en cada cambio y se borra cuando el flujo termina bien.
 */
interface PedidoDraftRepository {

    /** Guarda (o reemplaza) el borrador del vendedor. */
    suspend fun save(draft: PedidoDraft)

    /** Devuelve el borrador del vendedor, o null si no hay. */
    suspend fun get(vendedorId: String): PedidoDraft?

    /** Borra el borrador del vendedor. Idempotente. */
    suspend fun delete(vendedorId: String)
}
