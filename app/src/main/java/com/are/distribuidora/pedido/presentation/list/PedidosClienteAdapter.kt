package com.are.distribuidora.pedido.presentation.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R

class PedidosClienteAdapter(
    private val onPedidoClick: (PedidoClienteUiModel) -> Unit,
) : ListAdapter<PedidoClienteUiModel, PedidosClienteAdapter.ViewHolder>(DIFF) {

    var onDeleteClick: ((PedidoClienteUiModel) -> Unit)? = null
    var onEditClick:   ((PedidoClienteUiModel) -> Unit)? = null
    var onPrintClick: ((PedidoClienteUiModel) -> Unit)? = null
    var onSharePdfClick: ((PedidoClienteUiModel) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textInicial    = view.findViewById<TextView>(R.id.textClienteInicial)
        private val textNombre     = view.findViewById<TextView>(R.id.textClienteNombre)
        private val textSyncStatus = view.findViewById<TextView>(R.id.textSyncStatus)
        private val textFecha      = view.findViewById<TextView>(R.id.textFecha)
        private val textTotal      = view.findViewById<TextView>(R.id.textTotal)
        private val textItemCount  = view.findViewById<TextView>(R.id.textItemCount)
        private val menuButton     = view.findViewById<ImageButton>(R.id.menuButtonPedido)
        private val printButton    = view.findViewById<ImageButton>(R.id.printButtonPedido)

        private var currentItem: PedidoClienteUiModel? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let { onPedidoClick(it) }
            }
            menuButton.setOnClickListener { v ->
                currentItem?.let { showPopupMenu(v, it) }
            }
            printButton.setOnClickListener {
                currentItem?.let { onPrintClick?.invoke(it) }
            }
        }

        fun bind(item: PedidoClienteUiModel) {
            currentItem     = item
            textInicial.text    = initialsOf(item.clienteNombre)
            textNombre.text     = item.clienteNombre
            textSyncStatus.text = item.syncStatus
            textFecha.text      = item.creadoEnFormatted
            textTotal.text      = item.totalFormatted
            textItemCount.text  = itemView.context.getString(R.string.pedidos_item_count, item.itemCount)
        }

        private fun showPopupMenu(view: View, item: PedidoClienteUiModel) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_pedido_item, popup.menu)
            // Ocultar "Editar" si el pedido no es editable
            popup.menu.findItem(R.id.action_edit_pedido)?.isVisible = item.isEditable
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit_pedido -> {
                        onEditClick?.invoke(item)
                        true
                    }
                    R.id.action_delete_pedido -> {
                        onDeleteClick?.invoke(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_pedido_cliente, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        /** Iniciales del cliente para el avatar (1-2 letras en mayúscula). */
        fun initialsOf(name: String): String {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else            -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }

        private val DIFF = object : DiffUtil.ItemCallback<PedidoClienteUiModel>() {
            override fun areItemsTheSame(a: PedidoClienteUiModel, b: PedidoClienteUiModel) =
                a.pedidoId == b.pedidoId
            override fun areContentsTheSame(a: PedidoClienteUiModel, b: PedidoClienteUiModel) =
                a == b
        }
    }
}
