package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.Pedido
import com.are.distribuidora.domain.pedido.PedidoRepository
import javax.inject.Inject

class ListPedidosByClienteUseCase @Inject constructor(
    private val repository: PedidoRepository
) {
    suspend operator fun invoke(clienteId: String): Result<List<Pedido>> {
        return repository.listPedidosByCliente(clienteId)
    }
}
