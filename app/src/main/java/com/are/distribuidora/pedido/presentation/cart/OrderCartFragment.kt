package com.are.distribuidora.pedido.presentation.cart

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.pedido.presentation.create.CartItem
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.are.distribuidora.pedido.presentation.list.PedidosFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
@AndroidEntryPoint
class OrderCartFragment : Fragment(R.layout.fragment_order_cart) {
    private val flowViewModel: CreatePedidoFlowViewModel by activityViewModels()
    private val orderCartViewModel: OrderCartViewModel by viewModels()
    @Inject lateinit var applyItemDiscountUseCase: ApplyItemDiscountUseCase
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar           = view.findViewById<MaterialToolbar>(R.id.toolbarCart)
        val recyclerView      = view.findViewById<RecyclerView>(R.id.recyclerViewCart)
        val textEmpty         = view.findViewById<TextView>(R.id.textCartEmpty)
        val textTotal         = view.findViewById<TextView>(R.id.textCartTotal)
        val buttonContinue    = view.findViewById<MaterialButton>(R.id.buttonContinue)
        val buttonOtherOptions = view.findViewById<MaterialButton>(R.id.buttonOtherOptions)
        val progressBar       = view.findViewById<ProgressBar>(R.id.progressBarCart)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        val adapter = OrderCartAdapter(
            onEditQuantity = { item -> showEditQuantityDialog(item) },
            onEditDiscount = { item -> showEditDiscountDialog(item) },
            onRemove       = { item -> showRemoveConfirmation(item) },
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.attachToRecyclerView(recyclerView)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.cartItems.collect { cartMap ->
                    val items = cartMap.values.toList().sortedBy { it.name }
                    adapter.submitList(items)
                    textEmpty.visibility     = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility  = if (items.isEmpty()) View.GONE    else View.VISIBLE
                    buttonContinue.isEnabled = items.isNotEmpty()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.cartTotal.collect { total ->
                    textTotal.text = buildCurrencyFormat().format(total)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                orderCartViewModel.uiState.collect { state ->
                    when (state) {
                        is OrderCartViewModel.UiState.Idle -> {
                            progressBar?.visibility  = View.GONE
                            buttonContinue.isEnabled = flowViewModel.cartItems.value.isNotEmpty()
                        }
                        is OrderCartViewModel.UiState.Loading -> {
                            progressBar?.visibility  = View.VISIBLE
                            buttonContinue.isEnabled = false
                        }
                        is OrderCartViewModel.UiState.Success -> {
                            progressBar?.visibility = View.GONE
                            Toast.makeText(requireContext(), "Pedido creado correctamente", Toast.LENGTH_SHORT).show()
                            flowViewModel.clear()
                            orderCartViewModel.resetState()
                            // Notificar a PedidosFragment para que recargue la lista
                            parentFragmentManager.setFragmentResult(
                                PedidosFragment.RESULT_PEDIDO_CREATED,
                                Bundle.EMPTY
                            )
                            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        }
                        is OrderCartViewModel.UiState.Error -> {
                            progressBar?.visibility  = View.GONE
                            buttonContinue.isEnabled = flowViewModel.cartItems.value.isNotEmpty()
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            orderCartViewModel.resetState()
                        }
                        is OrderCartViewModel.UiState.DuplicateOrder -> {
                            progressBar?.visibility  = View.GONE
                            buttonContinue.isEnabled = flowViewModel.cartItems.value.isNotEmpty()
                            Snackbar.make(
                                view,
                                "Ya existe un pedido para este cliente hoy. Ve a la lista de pedidos y edítalo.",
                                Snackbar.LENGTH_LONG
                            ).show()
                            orderCartViewModel.resetState()
                        }
                    }
                }
            }
        }
        // ── Botón "Otras opciones" → PopupMenu sobre el propio botón ────────
        buttonOtherOptions.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.menu_cart_options, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_add_custom_item -> { showAddCustomItemDialog(); true }
                    R.id.action_clear_cart      -> { showClearCartConfirmation(); true }
                    else                        -> false
                }
            }
            popup.show()
        }

        buttonContinue.setOnClickListener {
            val items        = flowViewModel.cartItems.value.values.toList()
            val routeId      = flowViewModel.routeId.value
            val cliente      = flowViewModel.clienteSelection.value
            val deliveryDate = flowViewModel.deliveryDate.value
            orderCartViewModel.submitOrder(
                cartItems       = items,
                routeId         = routeId,
                cliente         = cliente,
                deliveryDate    = deliveryDate,
                descuentoGlobal = 0.0,
            )
        }
    }
    private fun showEditQuantityDialog(item: CartItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cart_edit_quantity, null)
        dialogView.findViewById<TextView>(R.id.dialogEditQtyProductName).text = item.name
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayoutQty)
        val input       = dialogView.findViewById<TextInputEditText>(R.id.inputQty)
        input.setText(item.quantity.toString())
        input.selectAll()
        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setView(dialogView)
            .create()
        dialogView.findViewById<View>(R.id.btnEditQtyCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnEditQtyConfirm).setOnClickListener {
            val raw = input.text?.toString()?.trim() ?: ""
            val qty = raw.toIntOrNull()
            when {
                qty == null || qty < 0 -> inputLayout.error = "Ingresa un numero entero valido"
                qty == 0 -> { dialog.dismiss(); showRemoveConfirmation(item) }
                else -> { inputLayout.error = null; flowViewModel.setQuantity(item.productId, qty); dialog.dismiss() }
            }
        }
        dialog.show()
        input.post { input.requestFocus() }
    }
    private fun showEditDiscountDialog(item: CartItem) {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_cart_discount, null)
        dialogView.findViewById<TextView>(R.id.dialogDiscountProductName).text = item.name
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayoutDiscount)
        val input       = dialogView.findViewById<TextInputEditText>(R.id.inputDiscount)
        val textResult  = dialogView.findViewById<TextView>(R.id.textDiscountResult)
        val nf          = buildCurrencyFormat()

        // ── A) Prefill sin truncar decimales ─────────────────────────────────
        // toInt() truncaba 10.5 → 10; ahora: entero→"10", decimal→"10.5".
        if (item.hasDiscount) {
            val pct = item.discountPercent
            val pctStr = if (pct == pct.toLong().toDouble()) {
                pct.toInt().toString()
            } else {
                "%.1f".format(pct)
            }
            input.setText(pctStr)
            input.selectAll()

            // ── C) Preview inicial coherente con el descuento ya existente ──
            // Muestra el descuento actual para que el usuario sepa qué va a reemplazar.
            val currentPctLabel = if (pct == pct.toLong().toDouble()) {
                pct.toInt().toString()
            } else {
                "%.1f".format(pct)
            }
            textResult.text = getString(R.string.cart_discount_current, currentPctLabel)
            textResult.visibility = View.VISIBLE
        }

        // ── TextWatcher: preview en tiempo real ──────────────────────────────
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                // ── B) Parseo robusto: acepta coma decimal de teclados locales ──
                val raw = s?.toString()?.trim() ?: ""
                val pct = raw.replace(',', '.').toDoubleOrNull()
                if (pct != null && pct in 0.0..100.0) {
                    val montoDescuento = applyItemDiscountUseCase(
                        precioUnitario = item.priceAmount,
                        cantidad       = item.quantity,
                        porcentaje     = pct,
                    )
                    val newSubtotal = (item.subtotalBase - montoDescuento).coerceAtLeast(0.0)
                    // ── C) Texto de preview explícito: "Nuevo subtotal (reemplaza descuento)" ──
                    textResult.text = getString(R.string.cart_discount_preview, nf.format(newSubtotal))
                    textResult.visibility = View.VISIBLE
                    inputLayout.error = null
                } else if (raw.isEmpty()) {
                    // Sin valor → sin preview
                    textResult.visibility = View.GONE
                    inputLayout.error = null
                } else {
                    // Valor fuera de rango o no numérico → ocultar preview y mostrar error
                    textResult.visibility = View.GONE
                }
            }
        })

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnDiscountCancel).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<View>(R.id.btnDiscountConfirm).setOnClickListener {
            // ── B) Parseo robusto en Confirmar: coma→punto ───────────────────
            val raw = input.text?.toString()?.trim() ?: ""

            if (raw.isEmpty()) {
                // Campo vacío → quitar descuento
                flowViewModel.setDiscount(item.productId, 0.0)
                dialog.dismiss()
                return@setOnClickListener
            }

            val parsed = raw.replace(',', '.').toDoubleOrNull()

            if (parsed == null) {
                // Texto no numérico
                inputLayout.error = getString(R.string.cart_discount_range_error)
                return@setOnClickListener
            }

            // ── D) Validación: clamp silencioso a [0, 100] ───────────────────
            // Valores > 100 o < 0 no se rechazan con error (UX consistente con
            // coerceIn); el usuario ve el resultado final en el preview y decide.
            val pct = parsed.coerceIn(0.0, 100.0)

            inputLayout.error = null
            flowViewModel.setDiscount(item.productId, pct)
            dialog.dismiss()
        }

        dialog.show()
        input.post { input.requestFocus() }
    }
    private fun showRemoveConfirmation(item: CartItem) {
        AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setTitle("Eliminar producto")
            .setMessage("Quitar \"${item.name}\" del carrito?")
            .setPositiveButton("Eliminar") { _, _ ->
                flowViewModel.removeFromCart(item.productId)
                Toast.makeText(requireContext(), "\"${item.name}\" eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun showClearCartConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setTitle(R.string.cart_clear_confirm_title)
            .setMessage(R.string.cart_clear_confirm_message)
            .setPositiveButton(R.string.cart_remove_confirm) { _, _ ->
                flowViewModel.clearCart()
            }
            .setNegativeButton(R.string.cart_action_cancel, null)
            .show()
    }

    private fun showAddCustomItemDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cart_custom_item, null)

        val layoutName  = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomName)
        val layoutQty   = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomQty)
        val layoutPrice = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomPrice)
        val inputName   = dialogView.findViewById<TextInputEditText>(R.id.inputCustomName)
        val inputQty    = dialogView.findViewById<TextInputEditText>(R.id.inputCustomQty)
        val inputPrice  = dialogView.findViewById<TextInputEditText>(R.id.inputCustomPrice)
        val inputNotes  = dialogView.findViewById<TextInputEditText>(R.id.inputCustomNotes)
        val textSubtotal = dialogView.findViewById<TextView>(R.id.textCustomSubtotal)
        val textError   = dialogView.findViewById<TextView>(R.id.textCustomError)

        val nf = buildCurrencyFormat()

        // Preview de subtotal en tiempo real
        val subtotalWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val qty   = inputQty.text?.toString()?.trim()?.toIntOrNull()
                val price = inputPrice.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (qty != null && qty > 0 && price != null && price >= 0.0) {
                    val subtotal = qty * price
                    textSubtotal.text = "Subtotal: ${nf.format(subtotal)}"
                    textSubtotal.visibility = View.VISIBLE
                } else {
                    textSubtotal.visibility = View.GONE
                }
            }
        }
        inputQty.addTextChangedListener(subtotalWatcher)
        inputPrice.addTextChangedListener(subtotalWatcher)

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setTitle(R.string.cart_custom_item_title)
            .setView(dialogView)
            .setPositiveButton("Agregar", null) // null para validar sin cerrar
            .setNegativeButton(R.string.cart_action_cancel, null)
            .create()

        dialog.setOnShowListener {
            val btnAgregar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnAgregar.setOnClickListener {
                // Limpiar errores previos
                layoutName.error  = null
                layoutQty.error   = null
                layoutPrice.error = null
                textError.visibility = View.GONE

                val nombre = inputName.text?.toString()?.trim().orEmpty()
                val qtyRaw = inputQty.text?.toString()?.trim().orEmpty()
                val priceRaw = inputPrice.text?.toString()?.trim()?.replace(',', '.').orEmpty()
                val notes  = inputNotes.text?.toString()?.trim()

                var hasError = false

                if (nombre.isBlank()) {
                    layoutName.error = "El nombre es obligatorio"
                    hasError = true
                }

                val qty = qtyRaw.toIntOrNull()
                if (qty == null || qty <= 0) {
                    layoutQty.error = "Ingresa una cantidad mayor a 0"
                    hasError = true
                }

                val price = priceRaw.toDoubleOrNull()
                if (price == null || price < 0.0) {
                    layoutPrice.error = "Ingresa un precio válido (≥ 0)"
                    hasError = true
                }

                if (hasError) return@setOnClickListener

                flowViewModel.addCustomItem(
                    name     = nombre,
                    quantity = qty!!,
                    price    = price!!,
                    notes    = notes,
                )
                dialog.dismiss()
                Toast.makeText(requireContext(), "\"$nombre\" agregado al carrito", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        inputName.post { inputName.requestFocus() }
    }

    private fun buildCurrencyFormat(): NumberFormat {
        val gt = Locale("es", "GT")
        return NumberFormat.getCurrencyInstance(gt).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }
}