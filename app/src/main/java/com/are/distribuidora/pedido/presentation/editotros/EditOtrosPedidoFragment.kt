package com.are.distribuidora.pedido.presentation.editotros

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
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
import com.are.distribuidora.pedido.presentation.common.CustomItemDialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla de edición de un pedido AJENO ("Otros Pedidos").
 *
 * - Lista de ítems con +/- y quitar (fuente única: [EditOtrosPedidoViewModel.uiState]).
 * - "Agregar ítem" → catálogo o ítem personalizado.
 * - "Guardar" → persiste local + encola subida a Firestore PRESERVANDO al vendedor original.
 *
 * Usa `activityViewModels()` para compartir el ViewModel con [EditOtrosCatalogFragment].
 */
@AndroidEntryPoint
class EditOtrosPedidoFragment : Fragment(R.layout.fragment_edit_otros_pedido) {

    private val viewModel: EditOtrosPedidoViewModel by activityViewModels()

    private val orderId by lazy { requireArguments().getString(ARG_ORDER_ID, "") }
    private val clientName by lazy { requireArguments().getString(ARG_CLIENT_NAME, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar   = view.findViewById<MaterialToolbar>(R.id.toolbarEditOtros)
        val recycler  = view.findViewById<RecyclerView>(R.id.recyclerEditOtros)
        val textEmpty = view.findViewById<TextView>(R.id.textEditOtrosEmpty)
        val textTotal = view.findViewById<TextView>(R.id.textEditOtrosTotal)
        val btnAddItem = view.findViewById<MaterialButton>(R.id.btnAddItemOtros)
        val btnSave   = view.findViewById<MaterialButton>(R.id.btnEditOtrosSave)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnEditOtrosCancel)
        val progress  = view.findViewById<ProgressBar>(R.id.progressEditOtros)

        if (clientName.isNotBlank()) toolbar.title = clientName
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = EditOtrosAdapter(
            onIncrement = { item -> viewModel.increment(item.localKey) },
            onDecrement = { item -> viewModel.decrement(item.localKey) },
            onDelete    = { item -> showRemoveItemDialog(item) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewModel.init(orderId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is EditOtrosPedidoViewModel.UiState.Loading -> {
                            progress.visibility  = View.VISIBLE
                            recycler.visibility  = View.GONE
                            textEmpty.visibility = View.GONE
                        }
                        is EditOtrosPedidoViewModel.UiState.Editing -> {
                            progress.visibility = View.GONE
                            if (clientName.isBlank() && state.clientName.isNotBlank()) {
                                toolbar.title = state.clientName
                            }
                            textTotal.text = state.totalFormatted
                            adapter.submitList(state.items)
                            val empty = state.items.isEmpty()
                            recycler.visibility  = if (empty) View.GONE else View.VISIBLE
                            textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                        }
                        is EditOtrosPedidoViewModel.UiState.Error -> {
                            progress.visibility = View.GONE
                            Snackbar.make(view, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSaving.collect { saving ->
                    btnSave.isEnabled = !saving
                    btnCancel.isEnabled = !saving
                    btnAddItem.isEnabled = !saving
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveEvent.collect { event ->
                    when (event) {
                        is EditOtrosPedidoViewModel.SaveEvent.Success -> {
                            Snackbar.make(view, R.string.edit_otros_saved, Snackbar.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                        is EditOtrosPedidoViewModel.SaveEvent.Error -> {
                            Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        btnAddItem.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menuInflater.inflate(R.menu.menu_add_item_options, menu)
                setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        R.id.action_add_item_catalog -> {
                            openCatalog()
                            true
                        }
                        R.id.action_add_item_custom -> {
                            CustomItemDialog.show(requireContext()) { name, qty, price, notes ->
                                viewModel.addCustomItem(name = name, quantity = qty, price = price, notes = notes)
                            }
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }

        btnSave.setOnClickListener { viewModel.save() }
        btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun openCatalog() {
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, EditOtrosCatalogFragment(), TAG_CATALOG)
            .addToBackStack(TAG_CATALOG)
            .commit()
    }

    private fun showRemoveItemDialog(item: EditOtrosItemUiModel) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_pedido_delete_item_title)
            .setMessage(getString(R.string.edit_otros_delete_item_message, item.nombre))
            .setPositiveButton(R.string.edit_pedido_delete_confirm) { _, _ ->
                viewModel.removeItem(item.localKey)
            }
            .setNegativeButton(R.string.pedido_delete_cancel, null)
            .show()
    }

    companion object {
        private const val ARG_ORDER_ID    = "orderId"
        private const val ARG_CLIENT_NAME = "clientName"
        private const val TAG_CATALOG     = "EDIT_OTROS_CATALOG"

        fun newInstance(orderId: String, clientName: String) =
            EditOtrosPedidoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                    putString(ARG_CLIENT_NAME, clientName)
                }
            }
    }
}
