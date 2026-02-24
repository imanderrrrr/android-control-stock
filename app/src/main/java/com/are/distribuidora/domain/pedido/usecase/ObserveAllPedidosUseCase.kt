package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.PedidoWithItems
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observa en tiempo real todos los pedidos locales con sus ítems.
 *
 * Emite una nueva lista cada vez que Room detecta un cambio en la tabla
 * `pedidos` o `pedido_items` (por ejemplo: PENDING_CREATE → SYNCING → SYNCED).
 * La UI no necesita recargar manualmente: el Flow lo hace automáticamente.
 *
 * Acepta un filtro de fecha opcional en formato "YYYY-MM-DD".
 * Si se pasa vacío o se omite, devuelve todos los pedidos sin filtrar.
 */
class ObserveAllPedidosUseCase @Inject constructor(
    private val repository: PedidoRepository,
) {
    operator fun invoke(deliveryDate: String = ""): Flow<List<PedidoWithItems>> =
        if (deliveryDate.isBlank()) repository.observeAllPedidosWithItems()
        else repository.observeAllPedidosWithItemsByDate(deliveryDate)
}

