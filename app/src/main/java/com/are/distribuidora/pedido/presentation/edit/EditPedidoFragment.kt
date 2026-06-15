package com.are.distribuidora.pedido.presentation.edit

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
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountByAmountUseCase
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.pedido.presentation.common.CustomItemDialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

/**
 * Pantalla de edición de un pedido propio.
 *
 * - Muestra la lista de ítems actuales con controles +/- , eliminar y descuento.
 * - Botón "Agregar ítem" → abre [EditPedidoCatalogFragment].
 * - Botón "Guardar" → llama a [EditPedidoViewModel.save].
 * - Botón "Cancelar" → vuelve atrás sin guardar.
 */
@AndroidEntryPoint
class EditPedidoFragment : Fragment(R.layout.fragment_edit_pedido) {

    private val viewModel: EditPedidoViewModel by activityViewModels()
    private val pedidoId by lazy { requireArguments().getString(ARG_PEDIDO_ID, "") }

    @Inject lateinit var applyItemDiscountUseCase: ApplyItemDiscountUseCase
    @Inject lateinit var applyItemDiscountByAmountUseCase: ApplyItemDiscountByAmountUseCase

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
            onDiscount  = { item -> showEditDiscountDialog(item) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewModel.init(pedidoId)

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
            .replace(R.id.fragmentContainer, EditPedidoCatalogFragment(), TAG_CATALOG)
            .addToBackStack(TAG_CATALOG)
            .commit()
    }

    // ── Diálogo de descuento con toggle % / Q ────────────────────────────────

    private fun showEditDiscountDialog(item: EditItemUiModel) {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_cart_discount, null)
        dialogView.findViewById<TextView>(R.id.dialogDiscountProductName).text = item.nombre
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleDiscountType)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayoutDiscount)
        val input       = dialogView.findViewById<TextInputEditText>(R.id.inputDiscount)
        val textResult  = dialogView.findViewById<TextView>(R.id.textDiscountResult)
        val nf          = buildCurrencyFormat()

        var currentType = item.discountType

        fun updateInputHint() {
            if (currentType == DiscountType.PERCENTAGE) {
                inputLayout.hint       = "Descuento (%)"
                inputLayout.helperText = "Ingresa un valor entre 0 y 100"
            } else {
                inputLayout.hint       = "Descuento (Q)"
                inputLayout.helperText = "Ingresa el monto en Quetzales"
            }
        }

        fun updatePreview(raw: String) {
            val value = raw.replace(',', '.').toDoubleOrNull()
            inputLayout.error = null
            if (value != null && value >= 0.0) {
                val discountMonto = if (currentType == DiscountType.PERCENTAGE) {
                    if (value > 100.0) { textResult.visibility = View.GONE; return }
                    applyItemDiscountUseCase(item.precioUnitario, item.cantidad, value)
                } else {
                    applyItemDiscountByAmountUseCase(item.precioUnitario, item.cantidad, value)
                }
                val newSubtotal = (item.subtotalBase - discountMonto).coerceAtLeast(0.0)
                textResult.text = getString(R.string.cart_discount_preview, nf.format(newSubtotal))
                textResult.visibility = View.VISIBLE
            } else {
                textResult.visibility = View.GONE
            }
        }

        toggleGroup.check(if (currentType == DiscountType.PERCENTAGE) R.id.btnDiscountPercent else R.id.btnDiscountAmount)
        updateInputHint()

        if (item.hasDiscount) {
            val prefillValue = if (item.discountType == DiscountType.PERCENTAGE) {
                val pct = item.discountPercent
                if (pct == pct.toLong().toDouble()) pct.toInt().toString() else "%.1f".format(pct)
            } else {
                val amt = item.descuentoItem
                if (amt == amt.toLong().toDouble()) amt.toInt().toString() else "%.2f".format(amt)
            }
            input.setText(prefillValue)
            input.selectAll()
            updatePreview(prefillValue)
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePreview(s?.toString()?.trim() ?: "")
            }
        })

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentType = if (checkedId == R.id.btnDiscountPercent) DiscountType.PERCENTAGE else DiscountType.AMOUNT
            updateInputHint()
            input.text?.clear()
            textResult.visibility = View.GONE
            inputLayout.error = null
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnDiscountCancel).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<View>(R.id.btnDiscountConfirm).setOnClickListener {
            val raw = input.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) {
                // Campo vacío → quitar descuento
                viewModel.setDiscount(item.localKey, 0.0)
                dialog.dismiss()
                return@setOnClickListener
            }
            val parsed = raw.replace(',', '.').toDoubleOrNull()
            if (parsed == null || parsed < 0.0) {
                inputLayout.error = getString(R.string.cart_discount_range_error)
                return@setOnClickListener
            }
            inputLayout.error = null
            if (currentType == DiscountType.PERCENTAGE) {
                viewModel.setDiscount(item.localKey, parsed.coerceIn(0.0, 100.0))
            } else {
                viewModel.setDiscountByAmount(item.localKey, parsed)
            }
            dialog.dismiss()
        }

        dialog.show()
        input.post { input.requestFocus() }
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

    private fun buildCurrencyFormat(): NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
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

