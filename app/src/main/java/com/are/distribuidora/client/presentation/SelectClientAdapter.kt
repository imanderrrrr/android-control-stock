package com.are.distribuidora.client.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.google.android.material.button.MaterialButton

class SelectClientAdapter(
    private val onClientClick: (ClientUiModel) -> Unit
) : ListAdapter<ClientUiModel, SelectClientAdapter.VH>(DIFF) {

    private var expandedClientId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_client_bosque, parent, false)
        return VH(view, ::onCardClicked, onClientClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item, isExpanded = item.id == expandedClientId)
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
        private val clientInitials: TextView = itemView.findViewById(R.id.clientInitials)
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val phoneText: TextView = itemView.findViewById(R.id.phoneText)
        private val addressText: TextView = itemView.findViewById(R.id.addressText)
        private val syncIndicatorText: TextView = itemView.findViewById(R.id.syncIndicatorText)
        private val activeIndicatorText: TextView = itemView.findViewById(R.id.activeIndicatorText)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
        private val selectClientButton: MaterialButton = itemView.findViewById(R.id.selectClientButton)

        init {
            menuButton.visibility = View.GONE
        }

        fun bind(item: ClientUiModel, isExpanded: Boolean) {
            nameText.text = item.name
            clientInitials.text = initialsOf(item.name)

            val address = item.address.orEmpty()
            addressText.text = address
            addressText.visibility = if (address.isBlank()) View.GONE else View.VISIBLE

            phoneText.text = item.phone
            phoneText.visibility = if (item.phone.isBlank()) View.GONE else View.VISIBLE

            syncIndicatorText.text = item.syncIndicatorText
            activeIndicatorText.text = item.activeIndicatorText

            selectClientButton.visibility = if (isExpanded) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onCardClick(item)
            }

            selectClientButton.setOnClickListener {
                onSelectButtonClick(item)
            }
        }

        private fun initialsOf(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> (parts[0].first().toString() + parts[1].first()).uppercase()
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ClientUiModel>() {
            override fun areItemsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
