package com.are.distribuidora.pedido.presentation.confirmed

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.list.PedidoDetalleFragment
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla de confirmación tras crear un pedido. Solo lectura: carga el pedido
 * recién creado por id desde Room y ofrece continuar la ruta o ver el detalle.
 */
@AndroidEntryPoint
class OrderConfirmedFragment : Fragment(R.layout.fragment_order_confirmed) {

    private val viewModel: OrderConfirmedViewModel by viewModels()

    private val pedidoId by lazy { requireArguments().getString(ARG_PEDIDO_ID, "") }
    private val clienteNombre by lazy { requireArguments().getString(ARG_CLIENTE_NOMBRE, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val textRef      = view.findViewById<TextView>(R.id.textConfirmedRef)
        val textName     = view.findViewById<TextView>(R.id.textConfirmedClientName)
        val textAddress  = view.findViewById<TextView>(R.id.textConfirmedClientAddress)
        val textProducts = view.findViewById<TextView>(R.id.textConfirmedProducts)
        val textTotal    = view.findViewById<TextView>(R.id.textConfirmedTotal)
        val btnContinue  = view.findViewById<MaterialButton>(R.id.btnContinueRoute)
        val btnDetail    = view.findViewById<MaterialButton>(R.id.btnViewDetail)

        // Nombre como fallback inmediato mientras carga desde Room.
        textName.text = clienteNombre

        viewModel.load(pedidoId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiModel.collect { ui ->
                    ui ?: return@collect
                    textRef.text      = ui.orderRef
                    textName.text     = ui.clientName
                    textAddress.text  = ui.clientAddress ?: ""
                    textAddress.visibility =
                        if (ui.clientAddress.isNullOrBlank()) View.GONE else View.VISIBLE
                    textProducts.text =
                        getString(R.string.order_confirmed_summary, ui.itemsCount, ui.unitsCount)
                    textTotal.text    = ui.totalFormatted
                }
            }
        }

        btnContinue.setOnClickListener { finishFlow() }

        btnDetail.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                .replace(
                    R.id.fragmentContainer,
                    PedidoDetalleFragment.newInstance(pedidoId = pedidoId, clienteNombre = clienteNombre),
                    "PEDIDO_DETALLE",
                )
                .addToBackStack("PEDIDO_DETALLE")
                .commit()
        }

        // Back del sistema = terminar el flujo (no volver al carrito ya consumido).
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finishFlow()
            },
        )
    }

    /** Cierra todo el flujo de creación, volviendo a la pantalla anterior (Inicio/Pedidos). */
    private fun finishFlow() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    companion object {
        private const val ARG_PEDIDO_ID = "pedidoId"
        private const val ARG_CLIENTE_NOMBRE = "clienteNombre"

        fun newInstance(pedidoId: String, clienteNombre: String) = OrderConfirmedFragment().apply {
            arguments = bundleOf(
                ARG_PEDIDO_ID to pedidoId,
                ARG_CLIENTE_NOMBRE to clienteNombre,
            )
        }
    }
}
