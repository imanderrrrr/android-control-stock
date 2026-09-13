package com.are.distribuidora.pedido.presentation.common

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.are.distribuidora.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Diálogo reutilizable para agregar un "ítem personalizado" (sin producto de catálogo):
 * nombre, cantidad, precio unitario y detalle opcional, con preview de subtotal en vivo.
 *
 * Extraído del flujo de creación (OrderCartFragment) para poder usarse también al EDITAR
 * pedidos propios (EditPedidoFragment) y ajenos (EditOtrosPedidoFragment). No tiene ninguna
 * dependencia con un ViewModel concreto: entrega el resultado por [onAdd].
 */
object CustomItemDialog {

    /**
     * @param onAdd se invoca solo cuando la validación pasa: nombre no vacío, cantidad > 0,
     *              precio ≥ 0. [notes] llega null si está en blanco.
     */
    fun show(
        context: Context,
        onAdd: (name: String, quantity: Int, price: Double, notes: String?) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_cart_custom_item, null)

        val layoutName  = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomName)
        val layoutQty   = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomQty)
        val layoutPrice = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomPrice)
        val inputName   = dialogView.findViewById<TextInputEditText>(R.id.inputCustomName)
        val inputQty    = dialogView.findViewById<TextInputEditText>(R.id.inputCustomQty)
        val inputPrice  = dialogView.findViewById<TextInputEditText>(R.id.inputCustomPrice)
        val inputNotes  = dialogView.findViewById<TextInputEditText>(R.id.inputCustomNotes)
        val textSubtotal = dialogView.findViewById<TextView>(R.id.textCustomSubtotal)
        val textError   = dialogView.findViewById<TextView>(R.id.textCustomError)

        val nf = NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }

        // Preview de subtotal en tiempo real.
        val subtotalWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val qty = inputQty.text?.toString()?.trim()?.toIntOrNull()
                val price = inputPrice.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (qty != null && qty > 0 && price != null && price >= 0.0) {
                    textSubtotal.text = "Subtotal: ${nf.format(qty * price)}"
                    textSubtotal.visibility = View.VISIBLE
                } else {
                    textSubtotal.visibility = View.GONE
                }
            }
        }
        inputQty.addTextChangedListener(subtotalWatcher)
        inputPrice.addTextChangedListener(subtotalWatcher)

        val dialog = AlertDialog.Builder(context, R.style.Theme_Distribuidora_Dialog_Light)
            .setTitle(R.string.cart_custom_item_title)
            .setView(dialogView)
            .setPositiveButton("Agregar", null) // null = validar sin cerrar
            .setNegativeButton(R.string.cart_action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                layoutName.error = null
                layoutQty.error = null
                layoutPrice.error = null
                textError.visibility = View.GONE

                val nombre = inputName.text?.toString()?.trim().orEmpty()
                val qtyRaw = inputQty.text?.toString()?.trim().orEmpty()
                val priceRaw = inputPrice.text?.toString()?.trim()?.replace(',', '.').orEmpty()
                val notes = inputNotes.text?.toString()?.trim()

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

                onAdd(nombre, qty!!, price!!, notes?.takeIf { it.isNotBlank() })
                dialog.dismiss()
            }
        }

        dialog.show()
        inputName.post { inputName.requestFocus() }
    }
}
