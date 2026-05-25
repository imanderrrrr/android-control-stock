package com.are.distribuidora.pedido.presentation.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R

/**
 * Adapter de solo lectura para la lista de ítems en el detalle de Otros Pedidos.
 * Reutiliza el layout item_pedido_detalle.xml (mismo que Mis Pedidos).
 */
class OtrosDetalleAdapter :
    ListAdapter<OtrosDetalleItemUiModel, OtrosDetalleAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textNombre    = view.findViewById<TextView>(R.id.textDetalleNombre)
        private val textPrecioQty = view.findViewById<TextView>(R.id.textDetallePrecioQty)
        private val textDescuento = view.findViewById<TextView>(R.id.textDetalleDescuento)
        private val textTotal     = view.findViewById<TextView>(R.id.textDetalleTotalItem)
        private val textNotes     = view.findViewById<TextView>(R.id.textDetalleNotes)

        fun bind(item: OtrosDetalleItemUiModel) {
            textNombre.text    = item.productName
            textPrecioQty.text = "${item.unitPriceFormatted} × ${item.quantity}"
            textTotal.text     = item.lineTotalFormatted
            // Otros Pedidos no tienen descuento por ítem → siempre oculto
            textDescuento.visibility = View.GONE

            val notes = item.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                textNotes.text       = "Det: $notes"
                textNotes.visibility = View.VISIBLE
            } else {
                textNotes.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pedido_detalle, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OtrosDetalleItemUiModel>() {
            override fun areItemsTheSame(a: OtrosDetalleItemUiModel, b: OtrosDetalleItemUiModel) =
                a.productId == b.productId
            override fun areContentsTheSame(a: OtrosDetalleItemUiModel, b: OtrosDetalleItemUiModel) =
                a == b
        }
    }
}

