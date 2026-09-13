package com.are.distribuidora.pedido.presentation.editotros

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Adapter editable para la lista de ítems al editar un pedido ajeno.
 *
 * `ListAdapter` + DiffUtil: la lista se entrega SIEMPRE vía [submitList] desde el único
 * StateFlow del ViewModel. No hay notify lateral (evita "Inconsistency detected").
 */
class EditOtrosAdapter(
    private val onIncrement: (EditOtrosItemUiModel) -> Unit,
    private val onDecrement: (EditOtrosItemUiModel) -> Unit,
    private val onDelete: (EditOtrosItemUiModel) -> Unit,
) : ListAdapter<EditOtrosItemUiModel, EditOtrosAdapter.ViewHolder>(DIFF) {

    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
        it.currency = Currency.getInstance("GTQ")
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textNombre   = view.findViewById<TextView>(R.id.textEditOtrosNombre)
        private val textPrecio   = view.findViewById<TextView>(R.id.textEditOtrosPrecio)
        private val textSubtotal = view.findViewById<TextView>(R.id.textEditOtrosSubtotal)
        private val textQty      = view.findViewById<TextView>(R.id.textEditOtrosQty)
        private val textNotes    = view.findViewById<TextView>(R.id.textEditOtrosNotes)
        private val btnDecr      = view.findViewById<ImageButton>(R.id.btnDecrOtrosQty)
        private val btnIncr      = view.findViewById<ImageButton>(R.id.btnIncrOtrosQty)
        private val btnDelete    = view.findViewById<ImageButton>(R.id.btnDeleteEditOtrosItem)

        private var current: EditOtrosItemUiModel? = null

        init {
            btnIncr.setOnClickListener { current?.let(onIncrement) }
            btnDecr.setOnClickListener { current?.let(onDecrement) }
            btnDelete.setOnClickListener { current?.let(onDelete) }
        }

        fun bind(item: EditOtrosItemUiModel) {
            current = item
            textNombre.text = item.nombre
            textPrecio.text = "${currencyFormat.format(item.precioUnitario)} c/u"
            textSubtotal.text = currencyFormat.format(item.subtotal)
            textQty.text = item.cantidad.toString()

            val notes = item.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                textNotes.text = "Det: $notes"
                textNotes.visibility = View.VISIBLE
            } else {
                textNotes.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_edit_otros, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EditOtrosItemUiModel>() {
            override fun areItemsTheSame(a: EditOtrosItemUiModel, b: EditOtrosItemUiModel) =
                a.localKey == b.localKey
            override fun areContentsTheSame(a: EditOtrosItemUiModel, b: EditOtrosItemUiModel) =
                a == b
        }
    }
}
