package com.are.distribuidora.pendingaccount.presentation.adapter

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
import com.are.distribuidora.pendingaccount.presentation.model.DueState
import com.are.distribuidora.pendingaccount.presentation.model.PendingAccountUiModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Adapter de la lista de cuentas por cobrar (rediseño Bosque Pro).
 */
class PendingAccountCardAdapter(
    private val onClick: (PendingAccountUiModel) -> Unit,
    private val onMarkPaid: (PendingAccountUiModel) -> Unit,
    private val onDelete: (PendingAccountUiModel) -> Unit,
) : ListAdapter<PendingAccountUiModel, PendingAccountCardAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_account_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<View>(R.id.cardRoot)
        private val banner = itemView.findViewById<TextView>(R.id.textOverdueBanner)
        private val image = itemView.findViewById<ImageView>(R.id.imageInvoice)
        private val name = itemView.findViewById<TextView>(R.id.textClientName)
        private val route = itemView.findViewById<TextView>(R.id.textRouteName)
        private val iconDue = itemView.findViewById<ImageView>(R.id.iconDue)
        private val due = itemView.findViewById<TextView>(R.id.textDueDate)
        private val amount = itemView.findViewById<TextView>(R.id.textAmount)
        private val btnPaid = itemView.findViewById<MaterialButton>(R.id.btnMarkPaid)
        private val btnDelete = itemView.findViewById<MaterialButton>(R.id.btnDelete)

        fun bind(item: PendingAccountUiModel) {
            val ctx = itemView.context
            name.text = item.clientName
            route.text = item.routeName
            due.text = item.dueLabel
            amount.text = item.amountFormatted

            val dueColor = when (item.dueState) {
                DueState.OVERDUE -> R.color.danger_text
                DueState.SOON -> R.color.warning_text
                DueState.NORMAL -> R.color.text_secondary
            }
            val dueColorInt = ContextCompat.getColor(ctx, dueColor)
            due.setTextColor(dueColorInt)
            iconDue.setColorFilter(dueColorInt)

            amount.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (item.isOverdue) R.color.danger_text else R.color.text_primary,
                )
            )

            if (item.overdueBannerText != null) {
                banner.visibility = View.VISIBLE
                banner.text = item.overdueBannerText
            } else {
                banner.visibility = View.GONE
            }

            bindThumbnail(item)

            card.setOnClickListener { onClick(item) }
            btnPaid.setOnClickListener { onMarkPaid(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }

        private fun bindThumbnail(item: PendingAccountUiModel) {
            val remote = item.invoiceRemoteUrl?.trim()?.takeIf { it.isNotEmpty() }
            val local = item.invoicePhotoUri?.trim()?.takeIf { it.isNotEmpty() }
            val source: Any? = when {
                remote != null -> remote
                local != null ->
                    if (local.startsWith("content://") || local.startsWith("file://")) local else File(local)
                else -> null
            }

            if (source != null) {
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setPadding(0, 0, 0, 0)
                image.clearColorFilter()
                Glide.with(image)
                    .load(source)
                    .override(160, 160)
                    .centerCrop()
                    .placeholder(R.drawable.ic_receipt_long)
                    .into(image)
            } else {
                Glide.with(image).clear(image)
                val pad = (16 * image.resources.displayMetrics.density).toInt()
                image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                image.setPadding(pad, pad, pad, pad)
                image.setImageResource(R.drawable.ic_receipt_long)
                image.setColorFilter(ContextCompat.getColor(image.context, R.color.text_tertiary))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PendingAccountUiModel>() {
            override fun areItemsTheSame(old: PendingAccountUiModel, new: PendingAccountUiModel) =
                old.id == new.id

            override fun areContentsTheSame(old: PendingAccountUiModel, new: PendingAccountUiModel) =
                old == new
        }
    }
}
