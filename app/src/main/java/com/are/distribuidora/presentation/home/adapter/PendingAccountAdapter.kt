package com.are.distribuidora.presentation.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.model.PendingAccountUiModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

class PendingAccountAdapter(
    private val onMarkPaid: (PendingAccountUiModel) -> Unit,
    private val onDelete: (PendingAccountUiModel) -> Unit,
    private val onPhotoClick: (PendingAccountUiModel) -> Unit,
) : ListAdapter<PendingAccountUiModel, PendingAccountAdapter.AccountVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_account, parent, false)
        return AccountVH(view)
    }

    override fun onBindViewHolder(holder: AccountVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card          = itemView as MaterialCardView
        private val overdueBadge  = itemView.findViewById<TextView>(R.id.textOverdueBadge)
        private val imageInvoice  = itemView.findViewById<ShapeableImageView>(R.id.imageInvoice)
        private val textClient    = itemView.findViewById<TextView>(R.id.textClientName)
        private val textRoute     = itemView.findViewById<TextView>(R.id.textRouteName)
        private val textDueDate   = itemView.findViewById<TextView>(R.id.textDueDate)
        private val textAmount    = itemView.findViewById<TextView>(R.id.textAmount)
        private val btnMarkPaid   = itemView.findViewById<MaterialButton>(R.id.btnMarkPaid)
        private val btnDelete     = itemView.findViewById<MaterialButton>(R.id.btnDelete)

        fun bind(item: PendingAccountUiModel) {
            textClient.text  = item.clientName
            textRoute.text   = itemView.context.getString(R.string.pending_route_label, item.routeName)
            textDueDate.text = itemView.context.getString(R.string.pending_due_prefix, item.dueDateFormatted)
            textAmount.text  = item.amountFormatted

            // Overdue badge
            if (item.isOverdue) {
                overdueBadge.visibility = View.VISIBLE
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.palette_800)
                card.strokeWidth = (2 * itemView.resources.displayMetrics.density).toInt()
                textDueDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.palette_800))
            } else {
                overdueBadge.visibility = View.GONE
                card.strokeWidth = 0
                textDueDate.setTextColor(ContextCompat.getColor(itemView.context, R.color.palette_600))
            }

            // Invoice photo: prioritize remote URL, then local
            val remoteUrl = item.invoiceRemoteUrl?.trim()?.takeIf { it.isNotEmpty() }
            val localUri = item.invoicePhotoUri?.trim()?.takeIf { it.isNotEmpty() }
            val imageSource: Any? = when {
                remoteUrl != null -> remoteUrl
                localUri != null -> {
                    if (localUri.startsWith("content://") || localUri.startsWith("file://")) localUri
                    else File(localUri)
                }
                else -> null
            }

            if (imageSource != null) {
                Glide.with(imageInvoice)
                    .load(imageSource)
                    .override(160, 160)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(imageInvoice)
            } else {
                imageInvoice.setImageResource(R.drawable.ic_image_placeholder)
            }

            // Toda la tarjeta abre el detalle
            card.setOnClickListener { onPhotoClick(item) }
            imageInvoice.setOnClickListener { onPhotoClick(item) }
            btnMarkPaid.setOnClickListener { onMarkPaid(item) }
            btnDelete.setOnClickListener { onDelete(item) }
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

