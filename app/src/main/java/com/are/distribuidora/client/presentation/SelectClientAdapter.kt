package com.are.distribuidora.client.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SelectClientAdapter(
    private val onClientClick: (ClientUiModel) -> Unit
) : ListAdapter<ClientUiModel, SelectClientAdapter.VH>(DIFF) {

    private var expandedClientId: String? = null

    /** Marca el primer cliente como "SIGUIENTE" (solo en Mi ruta sin búsqueda). */
    var showSiguiente: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_client_bosque, parent, false)
        return VH(view, ::onCardClicked, onClientClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(
            item,
            isExpanded = item.id == expandedClientId,
            isSiguiente = showSiguiente && position == 0,
        )
    }

    private fun onCardClicked(client: ClientUiModel) {
        val previous = expandedClientId
        expandedClientId = if (previous == client.id) null else client.id

        // refrescar solo los 2 items afectados (prev + nuevo)
        previous?.let { prevId ->
            val prevPos = currentList.indexOfFirst { it.id == prevId }
            if (prevPos >= 0) notifyItemChanged(prevPos)
        }
        val newPos = currentList.indexOfFirst { it.id == client.id }
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    class VH(
        itemView: View,
        private val onCardClick: (ClientUiModel) -> Unit,
        private val onSelectButtonClick: (ClientUiModel) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val avatarBox: FrameLayout = itemView.findViewById(R.id.avatarBox)
        private val clientInitials: TextView = itemView.findViewById(R.id.clientInitials)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val siguienteBadge: TextView = itemView.findViewById(R.id.siguienteBadge)
        private val addressText: TextView = itemView.findViewById(R.id.addressText)
        private val debtText: TextView = itemView.findViewById(R.id.debtText)
        private val selectClientButton: MaterialButton = itemView.findViewById(R.id.selectClientButton)

        fun bind(item: ClientUiModel, isExpanded: Boolean, isSiguiente: Boolean) {
            val ctx = itemView.context

            nameText.text = item.name
            clientInitials.text = initialsOf(item.name)

            // Avatar de color determinístico por cliente.
            val (avatarBg, avatarFg) = avatarPalette(item.id)
            avatarBox.backgroundTintList = ContextCompat.getColorStateList(ctx, avatarBg)
            clientInitials.setTextColor(ContextCompat.getColor(ctx, avatarFg))

            val address = item.address.orEmpty()
            addressText.text = address
            addressText.visibility = if (address.isBlank()) View.GONE else View.VISIBLE

            // Línea de deuda (cuentas por cobrar): pill ámbar/rojo si debe, "Al día" si no.
            if (item.debtText != null) {
                val bg = if (item.debtOverdue) R.color.danger_bg else R.color.warning_bg
                val fg = if (item.debtOverdue) R.color.danger_text else R.color.warning_text
                debtText.text = item.debtText
                debtText.setBackgroundResource(R.drawable.bg_pill)
                debtText.backgroundTintList = ContextCompat.getColorStateList(ctx, bg)
                debtText.setTextColor(ContextCompat.getColor(ctx, fg))
                debtText.setPadding(dp(10), dp(3), dp(10), dp(3))
            } else {
                debtText.text = "Al día"
                debtText.background = null
                debtText.backgroundTintList = null
                debtText.setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                debtText.setPadding(0, 0, 0, 0)
            }

            siguienteBadge.visibility = if (isSiguiente) View.VISIBLE else View.GONE
            card.strokeColor = ContextCompat.getColor(
                ctx,
                if (isSiguiente) R.color.brand_primary else R.color.border_subtle,
            )

            selectClientButton.visibility = if (isExpanded) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onCardClick(item) }
            selectClientButton.setOnClickListener { onSelectButtonClick(item) }
        }

        private fun dp(v: Int): Int =
            (v * itemView.resources.displayMetrics.density).toInt()

        private fun initialsOf(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> (parts[0].first().toString() + parts[1].first()).uppercase()
            }
        }

        private fun avatarPalette(id: String): Pair<Int, Int> {
            val palette = listOf(
                R.color.brand_soft to R.color.brand_soft_text,
                R.color.info_bg to R.color.info_text,
                R.color.warning_bg to R.color.warning_text,
                R.color.success_bg to R.color.success_text,
            )
            val idx = (id.hashCode() and 0x7fffffff) % palette.size
            return palette[idx]
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ClientUiModel>() {
            override fun areItemsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean =
                oldItem == newItem
        }
    }
}
