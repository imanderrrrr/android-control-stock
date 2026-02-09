package com.are.distribuidora.client.presentation

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
import com.are.distribuidora.client.presentation.model.ClientUiModel

class RouteClientsAdapter : ListAdapter<ClientUiModel, RouteClientsAdapter.VH>(DIFF) {

    var onDeleteClick: ((ClientUiModel) -> Unit)? = null
    var onEditClick: ((ClientUiModel) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_client, parent, false)
        return VH(view, onDeleteClick, onEditClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        itemView: View,
        private val onDeleteClick: ((ClientUiModel) -> Unit)?,
        private val onEditClick: ((ClientUiModel) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.nameText)
        private val phoneText: TextView = itemView.findViewById(R.id.phoneText)
        private val addressText: TextView = itemView.findViewById(R.id.addressText)
        private val syncIndicatorText: TextView = itemView.findViewById(R.id.syncIndicatorText)
        private val activeIndicatorText: TextView = itemView.findViewById(R.id.activeIndicatorText)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)

        private var currentClient: ClientUiModel? = null

        init {
            menuButton.setOnClickListener { view ->
                currentClient?.let { client ->
                    showPopupMenu(view, client)
                }
            }
        }

        fun bind(item: ClientUiModel) {
            currentClient = item
            nameText.text = item.name
            phoneText.text = item.phone
            addressText.text = item.address.orEmpty()
            syncIndicatorText.text = item.syncIndicatorText
            activeIndicatorText.text = item.activeIndicatorText
        }

        private fun showPopupMenu(view: View, client: ClientUiModel) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_client_item, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick?.invoke(client)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick?.invoke(client)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
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
