package com.are.distribuidora.pedido.presentation.edit

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla de edición de un pedido propio.
 *
 * - Muestra la lista de ítems actuales con controles +/- y botón eliminar.
 * - Botón "Agregar ítem" → abre [EditPedidoCatalogFragment].
 * - Botón "Guardar" → llama a [EditPedidoViewModel.save] que persiste
 *   atómicamente en Room y marca PENDING_UPDATE.
 * - Botón "Cancelar" → vuelve atrás sin guardar.
 *
 * Solo se muestra para pedidos propios (no para "Otros pedidos").
 */
@AndroidEntryPoint
class EditPedidoFragment : Fragment(R.layout.fragment_edit_pedido) {

    /**
     * Shared con EditPedidoCatalogFragment para que el catálogo pueda
     * agregar productos al carrito de edición sin necesidad de argumentos extra.
     */
    private val viewModel: EditPedidoViewModel by activityViewModels()

    private val pedidoId by lazy { requireArguments().getString(ARG_PEDIDO_ID, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar      = view.findViewById<MaterialToolbar>(R.id.toolbarEdit)
        val recycler     = view.findViewById<RecyclerView>(R.id.recyclerEditItems)
        val textTotal    = view.findViewById<TextView>(R.id.textEditTotal)
        val btnAddItem   = view.findViewById<MaterialButton>(R.id.btnAddItemEdit)
        val btnSave      = view.findViewById<MaterialButton>(R.id.btnEditSave)
        val btnCancel    = view.findViewById<MaterialButton>(R.id.btnEditCancel)
        val progress     = view.findViewById<ProgressBar>(R.id.progressEdit)

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = EditPedidoAdapter(
            onIncrement = { item -> viewModel.setQuantity(item.localKey, item.cantidad + 1) },
            onDecrement = { item -> viewModel.setQuantity(item.localKey, item.cantidad - 1) },
            onDelete    = { item -> showDeleteItemDialog(item) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // ── Inicializar el ViewModel con el pedidoId ─────────────────────────
        viewModel.init(pedidoId)

        // ── Observar estado de UI ────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is EditPedidoViewModel.UiState.Loading -> {
                            progress.visibility = View.VISIBLE
                            recycler.visibility = View.GONE
                        }
                        is EditPedidoViewModel.UiState.Editing -> {
                            progress.visibility = View.GONE
                            recycler.visibility = View.VISIBLE
                            toolbar.title = state.clienteNombre
                            textTotal.text = state.totalFormatted
                            adapter.submitList(state.items)
                        }
                        is EditPedidoViewModel.UiState.Error -> {
                            progress.visibility = View.GONE
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // ── Observar estado de guardado ──────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSaving.collect { saving ->
                    btnSave.isEnabled   = !saving
                    btnCancel.isEnabled = !saving
                    progress.visibility = if (saving) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveEvent.collect { event ->
                    when (event) {
                        is EditPedidoViewModel.SaveEvent.Success -> {
                            Snackbar.make(view, R.string.edit_pedido_saved, Snackbar.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                        is EditPedidoViewModel.SaveEvent.Error -> {
                            Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // ── Botones ──────────────────────────────────────────────────────────
        btnAddItem.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditPedidoCatalogFragment(), TAG_CATALOG)
                .addToBackStack(TAG_CATALOG)
                .commit()
        }

        btnSave.setOnClickListener { viewModel.save() }

        btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun showDeleteItemDialog(item: EditItemUiModel) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_pedido_delete_item_title)
            .setMessage(getString(R.string.edit_pedido_delete_item_message, item.nombre))
            .setPositiveButton(R.string.edit_pedido_delete_confirm) { _, _ ->
                viewModel.deleteItem(item.localKey)
            }
            .setNegativeButton(R.string.pedido_delete_cancel, null)
            .show()
    }

    companion object {
        private const val ARG_PEDIDO_ID = "pedidoId"
        private const val TAG_CATALOG   = "EDIT_CATALOG"

        fun newInstance(pedidoId: String) = EditPedidoFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PEDIDO_ID, pedidoId)
            }
        }
    }
}

