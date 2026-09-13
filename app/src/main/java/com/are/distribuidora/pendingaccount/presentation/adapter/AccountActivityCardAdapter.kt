package com.are.distribuidora.pendingaccount.presentation.adapter

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
import com.are.distribuidora.pendingaccount.presentation.model.AccountActivityUiModel
import com.google.android.material.card.MaterialCardView

/**
 * Adapter del log de actividad (cuentas pagadas / eliminadas).
 */
class AccountActivityCardAdapter :
    ListAdapter<AccountActivityUiModel, AccountActivityCardAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account_activity_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val icon = itemView.findViewById<ImageView>(R.id.iconAction)
        private val message = itemView.findViewById<TextView>(R.id.textActivityMessage)
        private val time = itemView.findViewById<TextView>(R.id.textActivityTime)

        fun bind(item: AccountActivityUiModel) {
            val ctx = itemView.context
            message.text = item.message

            val relative = DateUtils.getRelativeTimeSpanString(
                item.resolvedAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            time.text = ctx.getString(R.string.cxc_activity_time_amount, relative, item.amountFormatted)

            if (item.isPaid) {
                icon.setImageResource(R.drawable.ic_check_circle)
                icon.setColorFilter(ContextCompat.getColor(ctx, R.color.success_text))
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.success_bg))
            } else {
                icon.setImageResource(R.drawable.ic_delete)
                icon.setColorFilter(ContextCompat.getColor(ctx, R.color.text_secondary))
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_muted))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AccountActivityUiModel>() {
            override fun areItemsTheSame(old: AccountActivityUiModel, new: AccountActivityUiModel) =
                old.id == new.id

            override fun areContentsTheSame(old: AccountActivityUiModel, new: AccountActivityUiModel) =
                old == new
        }
    }
}
