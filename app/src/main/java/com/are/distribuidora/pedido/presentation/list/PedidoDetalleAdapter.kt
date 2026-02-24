package com.are.distribuidora.pedido.presentation.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R

class PedidoDetalleAdapter :
    ListAdapter<PedidoItemUiModel, PedidoDetalleAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textNombre    = view.findViewById<TextView>(R.id.textDetalleNombre)
        private val textPrecioQty = view.findViewById<TextView>(R.id.textDetallePrecioQty)
        private val textDescuento = view.findViewById<TextView>(R.id.textDetalleDescuento)
        private val textTotal     = view.findViewById<TextView>(R.id.textDetalleTotalItem)
        private val textNotes     = view.findViewById<TextView>(R.id.textDetalleNotes)

        fun bind(item: PedidoItemUiModel) {
            textNombre.text    = item.nombre
            textPrecioQty.text = "${item.precioUnitario} × ${item.cantidad}"
            textTotal.text     = item.totalItem

            if (item.descuentoFormatted != null) {
                textDescuento.text       = item.descuentoFormatted
                textDescuento.visibility = View.VISIBLE
            } else {
                textDescuento.visibility = View.GONE
            }

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
        LayoutInflater.from(parent.context).inflate(R.layout.item_pedido_detalle, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PedidoItemUiModel>() {
            override fun areItemsTheSame(a: PedidoItemUiModel, b: PedidoItemUiModel) =
                a.itemId == b.itemId
            override fun areContentsTheSame(a: PedidoItemUiModel, b: PedidoItemUiModel) =
                a == b
        }
    }
}

