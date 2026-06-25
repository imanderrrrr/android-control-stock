package com.are.distribuidora.pedido.presentation.cart

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountByAmountUseCase
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.pedido.presentation.common.FlowAnimations
import com.are.distribuidora.pedido.presentation.confirmed.OrderConfirmedFragment
import com.are.distribuidora.pedido.presentation.create.CartItem
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.are.distribuidora.pedido.presentation.list.PedidosFragment
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
@AndroidEntryPoint
class OrderCartFragment : Fragment(R.layout.fragment_order_cart) {
    private val flowViewModel: CreatePedidoFlowViewModel by activityViewModels()
    private val orderCartViewModel: OrderCartViewModel by viewModels()
    @Inject lateinit var applyItemDiscountUseCase: ApplyItemDiscountUseCase
    @Inject lateinit var applyItemDiscountByAmountUseCase: ApplyItemDiscountByAmountUseCase

    /** Total "rodante": animamos del valor anterior al nuevo. Cancelable. */
    private var totalAnimator: ValueAnimator? = null
    private var lastTotal: Double? = null
    /** La cascada de entrada de la lista se ejecuta una sola vez. */
    private var listCascaded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val recyclerView      = view.findViewById<RecyclerView>(R.id.recyclerViewCart)
        val textEmpty         = view.findViewById<TextView>(R.id.textCartEmpty)
        val textTotal         = view.findViewById<TextView>(R.id.textCartTotal)
        val textCliente       = view.findViewById<TextView>(R.id.textCartCliente)
        val textCount         = view.findViewById<TextView>(R.id.textCartCount)
        val textSubtotal      = view.findViewById<TextView>(R.id.textCartSubtotal)
        val textDiscounts     = view.findViewById<TextView>(R.id.textCartDiscounts)
        val rowDiscounts      = view.findViewById<View>(R.id.rowCartDiscounts)
        val buttonContinue    = view.findViewById<View>(R.id.buttonContinue)
        val buttonOtherOptions = view.findViewById<MaterialButton>(R.id.buttonOtherOptions)
        val progressBar       = view.findViewById<ProgressBar>(R.id.progressBarCart)
        val textOrderLimit    = view.findViewById<TextView>(R.id.textOrderLimit)
        val textIva           = view.findViewById<TextView>(R.id.textCartIva)
        val textCobrar        = view.findViewById<TextView>(R.id.textCartCobrar)
        val switchIva         = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchIva)
        switchIva.isChecked = flowViewModel.ivaEnabled.value
        switchIva.setOnCheckedChangeListener { _, checked -> flowViewModel.setIvaEnabled(checked) }
        view.findViewById<View>(R.id.btnBackCart).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.buttonDraft).setOnClickListener { parentFragmentManager.popBackStack() }
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
                    val playCascade = !listCascaded && items.isNotEmpty()
                    adapter.submitList(items) {
                        // commitCallback: la lista ya está aplicada → la cascada de
                        // entrada cae limpia sobre los ítems reales (una sola vez).
                        if (playCascade && isAdded) {
                            listCascaded = true
                            recyclerView.layoutAnimation =
                                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_fall_down)
                            recyclerView.scheduleLayoutAnimation()
                        }
                    }
                    textEmpty.visibility     = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility  = if (items.isEmpty()) View.GONE    else View.VISIBLE
                    buttonContinue.isEnabled = items.isNotEmpty()
                    buttonContinue.alpha = if (items.isNotEmpty()) 1f else 0.5f

                    val nf = buildCurrencyFormat()
                    textCount.text = getString(R.string.order_products_count, items.size)
                        .uppercase(Locale("es", "GT"))
                    val subtotalBase = items.sumOf { it.subtotalBase }
                    val discounts    = items.sumOf { it.discountAmount }
                    textSubtotal.text = nf.format(subtotalBase)
                    if (discounts > 0.0) {
                        textDiscounts.text = "-${nf.format(discounts)}"
                        rowDiscounts.visibility = View.VISIBLE
                    } else {
                        rowDiscounts.visibility = View.GONE
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.cartTotal.collect { total ->
                    val nf = buildCurrencyFormat()
                    // El total "rueda" del valor previo al nuevo (entrada: 0 → total;
                    // al activar IVA o editar ítems, rueda a la nueva cifra).
                    val from = lastTotal ?: 0.0
                    lastTotal = total
                    totalAnimator?.cancel()
                    totalAnimator = FlowAnimations.animateValue(from, total, duration = 480L) { v ->
                        val formatted = nf.format(v)
                        textTotal.text  = formatted
                        textCobrar.text = "COBRAR · $formatted"
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.cartIva.collect { iva ->
                    textIva.text = buildCurrencyFormat().format(iva)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.ivaEnabled.collect { on ->
                    if (switchIva.isChecked != on) switchIva.isChecked = on
                    if (on) {
                        // Solo animamos al pasar a visible (no en re-emisiones).
                        if (textIva.visibility != View.VISIBLE || textIva.alpha < 1f) {
                            textIva.visibility = View.VISIBLE
                            FlowAnimations.revealUp(textIva, duration = 300L, distanceDp = 9f)
                        }
                    } else {
                        textIva.animate().cancel()
                        textIva.alpha = 1f
                        textIva.translationY = 0f
                        textIva.visibility = View.INVISIBLE
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flowViewModel.clienteNombre.collect { name ->
                    textCliente.text = name ?: ""
                }
            }
        }
        // ── Indicador de límite de compra del cliente ────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(
                    flowViewModel.maxOrderAmountInCents,
                    flowViewModel.isOverLimit,
                    flowViewModel.cartTotal,
                ) { limit, over, total -> Triple(limit, over, total) }
                    .collect { (limit, over, _) ->
                        val nf = buildCurrencyFormat()
                        if (limit == null) {
                            textOrderLimit.visibility = View.GONE
                        } else {
                            textOrderLimit.visibility = View.VISIBLE
                            val limitFormatted = nf.format(limit / 100.0)
                            if (over) {
                                val totalFormatted = nf.format(flowViewModel.cartTotal.value)
                                textOrderLimit.text = getString(R.string.cart_order_limit_exceeded, limitFormatted, totalFormatted)
                                textOrderLimit.setTextColor(
                                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.palette_800)
                                )
                            } else {
                                textOrderLimit.text = getString(R.string.cart_order_limit, limitFormatted)
                                textOrderLimit.setTextColor(
                                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.palette_600)
                                )
                            }
                        }
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
                            val pedidoId   = state.pedidoId
                            val clientName = flowViewModel.clienteNombre.value ?: ""
                            orderCartViewModel.resetState()
                            flowViewModel.clear()
                            // Notificar a PedidosFragment para que recargue la lista
                            parentFragmentManager.setFragmentResult(
                                PedidosFragment.RESULT_PEDIDO_CREATED,
                                Bundle.EMPTY
                            )
                            // Mostrar la pantalla de confirmación (carga el pedido por id).
                            parentFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                                .replace(
                                    R.id.fragmentContainer,
                                    OrderConfirmedFragment.newInstance(pedidoId, clientName),
                                    "ORDER_CONFIRMED",
                                )
                                .addToBackStack("ORDER_CONFIRMED")
                                .commit()
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
                        is OrderCartViewModel.UiState.OrderLimitExceeded -> {
                            progressBar?.visibility  = View.GONE
                            buttonContinue.isEnabled = flowViewModel.cartItems.value.isNotEmpty()
                            val nf = buildCurrencyFormat()
                            val limitStr = nf.format(state.limitInCents / 100.0)
                            val totalStr = nf.format(state.totalInCents / 100.0)
                            Snackbar.make(
                                view,
                                getString(R.string.cart_order_limit_exceeded_error, totalStr, limitStr),
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
                applyIva        = flowViewModel.ivaEnabled.value,
            )
        }
    }

    override fun onDestroyView() {
        totalAnimator?.cancel()
        totalAnimator = null
        super.onDestroyView()
    }

    private fun showEditQuantityDialog(item: CartItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cart_edit_quantity, null)
        dialogView.findViewById<TextView>(R.id.dialogEditQtyProductName).text = item.name
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayoutQty)
        val input       = dialogView.findViewById<TextInputEditText>(R.id.inputQty)
        input.setText(item.quantity.toString())
        input.selectAll()
        val inputNotes = dialogView.findViewById<TextInputEditText>(R.id.inputNotes)
        inputNotes.setText(item.notes.orEmpty())
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
                else -> {
                    inputLayout.error = null
                    flowViewModel.setQuantity(item.productId, qty)
                    flowViewModel.setNotes(item.productId, inputNotes.text?.toString())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        input.post { input.requestFocus() }
    }
    private fun showEditDiscountDialog(item: CartItem) {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_cart_discount, null)
        dialogView.findViewById<TextView>(R.id.dialogDiscountProductName).text = item.name
        val toggleGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleDiscountType)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayoutDiscount)
        val input       = dialogView.findViewById<TextInputEditText>(R.id.inputDiscount)
        val textResult  = dialogView.findViewById<TextView>(R.id.textDiscountResult)
        val nf          = buildCurrencyFormat()

        // Estado local del tipo de descuento
        var currentType = item.discountType

        // ── Función auxiliar: actualizar hint y helper text del campo según tipo ──
        fun updateInputHint() {
            if (currentType == DiscountType.PERCENTAGE) {
                inputLayout.hint       = "Descuento (%)"
                inputLayout.helperText = "Ingresa un valor entre 0 y 100"
            } else {
                inputLayout.hint       = "Descuento (Q)"
                inputLayout.helperText = "Ingresa el monto en Quetzales"
            }
        }

        // ── Función auxiliar: calcular y mostrar preview ──────────────────────
        fun updatePreview(raw: String) {
            val value = raw.replace(',', '.').toDoubleOrNull()
            inputLayout.error = null
            if (value != null && value >= 0.0) {
                val discountMonto = if (currentType == DiscountType.PERCENTAGE) {
                    if (value > 100.0) { textResult.visibility = View.GONE; return }
                    applyItemDiscountUseCase(item.priceAmount, item.quantity, value)
                } else {
                    applyItemDiscountByAmountUseCase(item.priceAmount, item.quantity, value)
                }
                val newSubtotal = (item.subtotalBase - discountMonto).coerceAtLeast(0.0)
                textResult.text = getString(R.string.cart_discount_preview, nf.format(newSubtotal))
                textResult.visibility = View.VISIBLE
            } else {
                textResult.visibility = View.GONE
            }
        }

        // ── Selección inicial del toggle ──────────────────────────────────────
        toggleGroup.check(if (currentType == DiscountType.PERCENTAGE) R.id.btnDiscountPercent else R.id.btnDiscountAmount)
        updateInputHint()

        // ── Prefill con el valor actual ───────────────────────────────────────
        if (item.hasDiscount) {
            val prefillValue = if (item.discountType == DiscountType.PERCENTAGE) {
                val pct = item.discountPercent
                if (pct == pct.toLong().toDouble()) pct.toInt().toString() else "%.1f".format(pct)
            } else {
                val amt = item.discountAmount
                if (amt == amt.toLong().toDouble()) amt.toInt().toString() else "%.2f".format(amt)
            }
            input.setText(prefillValue)
            input.selectAll()
            updatePreview(prefillValue)
        }

        // ── TextWatcher: preview en tiempo real ──────────────────────────────
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePreview(s?.toString()?.trim() ?: "")
            }
        })

        // ── Listener del toggle ───────────────────────────────────────────────
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
                flowViewModel.setDiscount(item.productId, 0.0)
                dialog.dismiss()
                return@setOnClickListener
            }

            val parsed = raw.replace(',', '.').toDoubleOrNull()
            if (parsed == null || parsed < 0.0) {
                inputLayout.error = getString(R.string.cart_discount_range_error)
                return@setOnClickListener
            }

            if (currentType == DiscountType.PERCENTAGE) {
                val pct = parsed.coerceIn(0.0, 100.0)
                inputLayout.error = null
                flowViewModel.setDiscount(item.productId, pct)
            } else {
                inputLayout.error = null
                flowViewModel.setDiscountByAmount(item.productId, parsed)
            }
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