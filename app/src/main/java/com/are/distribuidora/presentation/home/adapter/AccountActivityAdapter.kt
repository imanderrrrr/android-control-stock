package com.are.distribuidora.presentation.home.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.model.AccountActivityUiModel

class AccountActivityAdapter :
    ListAdapter<AccountActivityUiModel, AccountActivityAdapter.ActivityVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account_activity, parent, false)
        return ActivityVH(view)
    }

    override fun onBindViewHolder(holder: ActivityVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActivityVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconAction = itemView.findViewById<ImageView>(R.id.iconAction)
        private val textMessage = itemView.findViewById<TextView>(R.id.textActivityMessage)
        private val textTime = itemView.findViewById<TextView>(R.id.textActivityTime)
        private val card = itemView as com.google.android.material.card.MaterialCardView

        fun bind(item: AccountActivityUiModel) {
            textMessage.text = item.message

            // Tiempo relativo + monto
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                item.resolvedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            textTime.text = "$relativeTime — ${item.amountFormatted}"

            // Estilo según tipo de acción
            when (item.actionType) {
                "PAID" -> {
                    iconAction.setImageResource(R.drawable.ic_check_circle)
                    iconAction.setColorFilter(ContextCompat.getColor(itemView.context, R.color.app_primary))
                    card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.palette_050))
                }
                "DELETED" -> {
                    iconAction.setImageResource(R.drawable.ic_delete)
                    iconAction.setColorFilter(ContextCompat.getColor(itemView.context, R.color.palette_600))
                    card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.palette_100))
                }
                else -> {
                    iconAction.setImageResource(R.drawable.ic_check_circle)
                    iconAction.setColorFilter(ContextCompat.getColor(itemView.context, R.color.palette_600))
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AccountActivityUiModel>() {
            override fun areItemsTheSame(
                old: AccountActivityUiModel,
                new: AccountActivityUiModel,
            ) = old.id == new.id

            override fun areContentsTheSame(
                old: AccountActivityUiModel,
                new: AccountActivityUiModel,
            ) = old == new
        }
    }
}
