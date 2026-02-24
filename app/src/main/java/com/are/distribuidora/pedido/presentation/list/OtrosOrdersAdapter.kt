package com.are.distribuidora.pedido.presentation.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R

/**
 * Adapter de nivel superior: muestra tarjetas de Ruta.
 * Cada tarjeta de ruta contiene un RecyclerView anidado con los headers de pedidos.
 */
class OtrosOrdersAdapter(
    private val onDownloadClick: (OtrosOrderHeaderUiModel) -> Unit,
    private val onOpenClick: (OtrosOrderHeaderUiModel) -> Unit,
) : ListAdapter<OtrosRouteUiModel, OtrosOrdersAdapter.RouteViewHolder>(ROUTE_DIFF) {

    /** Set de orderId actualmente en proceso de descarga (muestra spinner). */
    var downloadingIds: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textRouteName  = view.findViewById<TextView>(R.id.textOtrosRouteName)
        private val textOrderCount = view.findViewById<TextView>(R.id.textOtrosOrderCount)
        private val recyclerOrders = view.findViewById<RecyclerView>(R.id.recyclerOtrosOrders)

        private val headersAdapter = OtrosOrderHeaderAdapter(
            onDownloadClick = { header -> onDownloadClick(header) },
            onOpenClick     = { header -> onOpenClick(header) },
        )

        init {
            recyclerOrders.layoutManager = LinearLayoutManager(view.context)
            recyclerOrders.adapter = headersAdapter
            // Evitar scroll anidado conflictivo
            recyclerOrders.isNestedScrollingEnabled = false
        }

        fun bind(item: OtrosRouteUiModel) {
            textRouteName.text  = item.routeName
            textOrderCount.text = itemView.context.getString(
                R.string.otros_orders_route_subtitle, item.orderCount
            )
            headersAdapter.downloadingIds = downloadingIds
            headersAdapter.submitList(item.orders)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder =
        RouteViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_otros_route, parent, false)
        )

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) =
        holder.bind(getItem(position))

    // Necesario para propagador downloadingIds a los holders ya vinculados
    override fun onBindViewHolder(holder: RouteViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
    }

    companion object {
        private val ROUTE_DIFF = object : DiffUtil.ItemCallback<OtrosRouteUiModel>() {
            override fun areItemsTheSame(a: OtrosRouteUiModel, b: OtrosRouteUiModel) =
                a.routeId == b.routeId
            override fun areContentsTheSame(a: OtrosRouteUiModel, b: OtrosRouteUiModel) =
                a == b
        }
    }
}

/**
 * Adapter de nivel secundario: headers de pedidos dentro de una ruta.
 */
class OtrosOrderHeaderAdapter(
    private val onDownloadClick: (OtrosOrderHeaderUiModel) -> Unit,
    private val onOpenClick: (OtrosOrderHeaderUiModel) -> Unit,
) : ListAdapter<OtrosOrderHeaderUiModel, OtrosOrderHeaderAdapter.HeaderViewHolder>(HEADER_DIFF) {

    var downloadingIds: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textClientName    = view.findViewById<TextView>(R.id.textOtrosClientName)
        private val textCreatedAt     = view.findViewById<TextView>(R.id.textOtrosCreatedAt)
        private val textAddress       = view.findViewById<TextView>(R.id.textOtrosAddress)
        private val textSeller        = view.findViewById<TextView>(R.id.textOtrosSeller)
        private val textItemsCount    = view.findViewById<TextView>(R.id.textOtrosItemsCount)
        private val textStatusLabel   = view.findViewById<TextView>(R.id.textOtrosStatusLabel)
        private val textTotal         = view.findViewById<TextView>(R.id.textOtrosTotal)
        private val btnDownload       = view.findViewById<ImageButton>(R.id.btnOtrosDownload)
        private val progressDownload  = view.findViewById<ProgressBar>(R.id.progressOtrosDownload)

        fun bind(item: OtrosOrderHeaderUiModel) {
            textClientName.text  = item.clientName
            textCreatedAt.text   = item.createdAtFormatted
            textAddress.text     = item.clientAddress ?: ""
            textAddress.visibility = if (item.clientAddress.isNullOrBlank()) View.GONE else View.VISIBLE
            textSeller.text      = item.sellerName?.let {
                itemView.context.getString(R.string.otros_seller_label, it)
            } ?: ""
            textSeller.visibility = if (item.sellerName.isNullOrBlank()) View.GONE else View.VISIBLE
            textItemsCount.text  = itemView.context.getString(
                R.string.otros_items_count, item.itemsCount
            )
            textStatusLabel.text = item.downloadStatusLabel
            textTotal.text       = item.totalFormatted ?: ""
            textTotal.visibility = if (item.totalFormatted == null) View.GONE else View.VISIBLE

            val isDownloading = downloadingIds.contains(item.orderId)

            // Tap en tarjeta completa → siempre propaga al Fragment para decidir qué hacer
            itemView.setOnClickListener { onOpenClick(item) }

            when {
                isDownloading -> {
                    btnDownload.visibility      = View.GONE
                    progressDownload.visibility = View.VISIBLE
                }
                item.downloadStatus == OtrosDownloadUiStatus.COMPLETED -> {
                    // Ya descargado: ocultar botón, tap en tarjeta abre directamente
                    btnDownload.visibility      = View.GONE
                    progressDownload.visibility = View.GONE
                }
                else -> {
                    // PENDING / FAILED: mostrar botón de descarga
                    btnDownload.visibility      = View.VISIBLE
                    progressDownload.visibility = View.GONE
                    btnDownload.setOnClickListener { onDownloadClick(item) }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder =
        HeaderViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_otros_order_header, parent, false)
        )

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val HEADER_DIFF = object : DiffUtil.ItemCallback<OtrosOrderHeaderUiModel>() {
            override fun areItemsTheSame(a: OtrosOrderHeaderUiModel, b: OtrosOrderHeaderUiModel) =
                a.orderId == b.orderId
            override fun areContentsTheSame(a: OtrosOrderHeaderUiModel, b: OtrosOrderHeaderUiModel) =
                a == b
        }
    }
}

