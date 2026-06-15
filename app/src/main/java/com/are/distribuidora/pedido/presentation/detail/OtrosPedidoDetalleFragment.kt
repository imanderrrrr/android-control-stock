package com.are.distribuidora.pedido.presentation.detail

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.editotros.EditOtrosPedidoFragment
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla de detalle de un pedido de "Otros Pedidos".
 *
 * Consulta Room (tabla order_items) via [OtrosPedidoDetalleViewModel].
 * NO comparte datos con PedidoDetalleFragment ni con allPedidos de Mis Pedidos.
 *
 * Navegación: se abre desde OtrosPedidosListFragment vía FragmentManager.
 * Argumento requerido: ARG_ORDER_ID (String).
 */
@AndroidEntryPoint
class OtrosPedidoDetalleFragment : Fragment(R.layout.fragment_otros_pedido_detalle) {

    private val viewModel: OtrosPedidoDetalleViewModel by viewModels()

    private val orderId     by lazy { requireArguments().getString(ARG_ORDER_ID, "") }
    private val clientName  by lazy { requireArguments().getString(ARG_CLIENT_NAME, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar       = view.findViewById<MaterialToolbar>(R.id.toolbarOtrosDetalle)
        val progress      = view.findViewById<ProgressBar>(R.id.progressOtrosDetalle)
        val textEmpty     = view.findViewById<TextView>(R.id.textOtrosDetalleEmpty)
        val layoutContent = view.findViewById<View>(R.id.layoutOtrosDetalleContent)
        val recycler      = view.findViewById<RecyclerView>(R.id.recyclerOtrosDetalle)
        val textSeller    = view.findViewById<TextView>(R.id.textOtrosDetalleSeller)
        val textTotal     = view.findViewById<TextView>(R.id.textOtrosDetalleTotal)

        toolbar.title = clientName.ifBlank { getString(R.string.pedidos_detalle_title) }
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Acción "Editar": abre el editor de pedido ajeno (agregar/quitar ítems).
        toolbar.inflateMenu(R.menu.menu_otros_detalle)
        toolbar.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.action_edit_otros -> {
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.fragmentContainer,
                            EditOtrosPedidoFragment.newInstance(orderId = orderId, clientName = clientName),
                            TAG_EDIT_OTROS,
                        )
                        .addToBackStack(TAG_EDIT_OTROS)
                        .commit()
                    true
                }
                else -> false
            }
        }

        val adapter = OtrosDetalleAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is OtrosPedidoDetalleViewModel.UiState.Loading -> {
                            progress.visibility      = View.VISIBLE
                            textEmpty.visibility     = View.GONE
                            layoutContent.visibility = View.GONE
                        }
                        is OtrosPedidoDetalleViewModel.UiState.Empty -> {
                            progress.visibility      = View.GONE
                            textEmpty.visibility     = View.VISIBLE
                            layoutContent.visibility = View.GONE
                            textEmpty.text = getString(R.string.otros_detalle_empty)
                        }
                        is OtrosPedidoDetalleViewModel.UiState.Success -> {
                            progress.visibility      = View.GONE
                            textEmpty.visibility     = View.GONE
                            layoutContent.visibility = View.VISIBLE

                            // Actualizar título si el toolbar aún usa el valor de args
                            if (toolbar.title.isNullOrBlank()) {
                                toolbar.title = state.clientName
                            }

                            if (!state.sellerName.isNullOrBlank()) {
                                textSeller.text = getString(
                                    R.string.otros_seller_label, state.sellerName
                                )
                                textSeller.visibility = View.VISIBLE
                            } else {
                                textSeller.visibility = View.GONE
                            }

                            adapter.submitList(state.items)
                            textTotal.text = state.totalFormatted
                        }
                        is OtrosPedidoDetalleViewModel.UiState.Error -> {
                            progress.visibility      = View.GONE
                            textEmpty.visibility     = View.VISIBLE
                            layoutContent.visibility = View.GONE
                            textEmpty.text = state.message
                        }
                    }
                }
            }
        }

        // Cargar datos al crearse la vista
        viewModel.load(orderId)
    }

    companion object {
        private const val ARG_ORDER_ID    = "orderId"
        private const val ARG_CLIENT_NAME = "clientName"
        private const val TAG_EDIT_OTROS  = "EDIT_OTROS"

        fun newInstance(orderId: String, clientName: String) =
            OtrosPedidoDetalleFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                    putString(ARG_CLIENT_NAME, clientName)
                }
            }
    }
}

