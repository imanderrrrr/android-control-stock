package com.are.distribuidora.pedido.presentation.list

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla 3 — Detalle de un pedido: lista de ítems (carrito de solo lectura) + total.
 * Usa activityViewModels() para compartir la instancia ya cargada por PedidosFragment
 * y evitar la race condition donde allPedidos aún está vacío.
 */
@AndroidEntryPoint
class PedidoDetalleFragment : Fragment(R.layout.fragment_pedido_detalle) {

    // Misma instancia del VM que PedidosFragment y PedidosPorRutaFragment
    private val viewModel: PedidosViewModel by activityViewModels()

    private val pedidoId      by lazy { requireArguments().getString(ARG_PEDIDO_ID, "") }
    private val clienteNombre by lazy { requireArguments().getString(ARG_CLIENTE_NOMBRE, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar    = view.findViewById<MaterialToolbar>(R.id.toolbarDetalle)
        val recycler   = view.findViewById<RecyclerView>(R.id.recyclerDetalle)
        val textTotal  = view.findViewById<TextView>(R.id.textTotalDetalle)

        toolbar.title = clienteNombre
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = PedidoDetalleAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Observar uiState para no leer allPedidos antes de que termine la carga asíncrona
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is PedidosViewModel.UiState.Success) {
                        val items = viewModel.getItemsByPedido(pedidoId)
                        adapter.submitList(items)
                        textTotal.text = viewModel.getTotalPedido(pedidoId)
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_PEDIDO_ID      = "pedidoId"
        private const val ARG_CLIENTE_NOMBRE = "clienteNombre"

        fun newInstance(pedidoId: String, clienteNombre: String) =
            PedidoDetalleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PEDIDO_ID, pedidoId)
                    putString(ARG_CLIENTE_NOMBRE, clienteNombre)
                }
            }
    }
}
