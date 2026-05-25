package com.are.distribuidora.crash.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.crash.data.CrashReportRepository.CrashReportSummary

/**
 * Adapter simple para la lista de reportes de crash. Cada item expone
 * tres acciones: tap = abrir, share = compartir vía FileProvider,
 * delete = borrar archivo.
 */
class CrashReportsAdapter(
    private val onClick: (CrashReportSummary) -> Unit,
    private val onShare: (CrashReportSummary) -> Unit,
    private val onDelete: (CrashReportSummary) -> Unit,
) : RecyclerView.Adapter<CrashReportsAdapter.VH>() {

    private val items = mutableListOf<CrashReportSummary>()

    fun submit(newItems: List<CrashReportSummary>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crash_report, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate: TextView = itemView.findViewById(R.id.textDate)
        private val textSize: TextView = itemView.findViewById(R.id.textSize)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: CrashReportSummary) {
            textDate.text = item.displayName
            textSize.text = humanReadableSize(item.sizeBytes)
            itemView.setOnClickListener { onClick(item) }
            btnShare.setOnClickListener { onShare(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private fun humanReadableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        return String.format("%.1f MB", kb / 1024.0)
    }
}
