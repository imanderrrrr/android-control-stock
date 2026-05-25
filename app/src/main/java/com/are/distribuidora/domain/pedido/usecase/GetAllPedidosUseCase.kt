package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import javax.inject.Inject

/**
 * Obtiene todos los pedidos locales con sus ítems.
 * La agrupación por ruta es responsabilidad de la capa de presentación (UI model).
 */
class GetAllPedidosUseCase @Inject constructor(
    private val repository: PedidoRepository
) {
    suspend operator fun invoke(): List<PedidoWithItems> =
        repository.getAllPedidosWithItems()
}

