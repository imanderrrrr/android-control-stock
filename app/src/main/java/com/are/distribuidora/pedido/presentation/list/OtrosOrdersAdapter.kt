package com.are.distribuidora.pedido.presentation.list

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
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
    private val onPrintClick: (OtrosOrderHeaderUiModel) -> Unit = {},
    private val onSharePdfClick: (OtrosOrderHeaderUiModel) -> Unit = {},
) : ListAdapter<OtrosRouteUiModel, OtrosOrdersAdapter.RouteViewHolder>(ROUTE_DIFF) {

    /** Set de orderId actualmente en proceso de descarga (muestra spinner). */
    var downloadingIds: Set<String> = emptySet()
        set(value) {
            field = value
            // Usar payload para que solo se actualice el estado de descarga
            // sin rehacer todo el layout (evita parpadeos y posibles inconsistencias).
            val count = itemCount
            if (count > 0) {
                notifyItemRangeChanged(0, count, PAYLOAD_DOWNLOADING)
            }
        }

    inner class RouteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textRouteName  = view.findViewById<TextView>(R.id.textOtrosRouteName)
        private val textOrderCount = view.findViewById<TextView>(R.id.textOtrosOrderCount)
        private val textRouteTotal = view.findViewById<TextView>(R.id.textOtrosRouteTotal)
        private val recyclerOrders = view.findViewById<RecyclerView>(R.id.recyclerOtrosOrders)

        private val headersAdapter = OtrosOrderHeaderAdapter(
            onDownloadClick = { header -> onDownloadClick(header) },
            onOpenClick     = { header -> onOpenClick(header) },
            onPrintClick    = { header -> onPrintClick(header) },
        )

        init {
            recyclerOrders.layoutManager = LinearLayoutManager(view.context)
            recyclerOrders.adapter = headersAdapter
            // Evitar scroll anidado conflictivo
            recyclerOrders.isNestedScrollingEnabled = false

            // Swipe-LEFT en cada tarjeta de pedido (solo si está descargado) → compartir PDF.
            // Mismo gesto que PedidosClienteAdapter en "Mis Pedidos" para mantener consistencia.
            ItemTouchHelper(SharePdfSwipeCallback(view, headersAdapter, onSharePdfClick))
                .attachToRecyclerView(recyclerOrders)
        }

        fun bind(item: OtrosRouteUiModel) {
            textRouteName.text  = item.routeName
            textOrderCount.text = itemView.context.getString(
                R.string.otros_orders_route_subtitle, item.orderCount
            )
            // Total acumulado: solo se muestra cuando hay al menos un pedido descargado
            if (item.routeTotalFormatted != null) {
                textRouteTotal.text       = item.routeTotalFormatted
                textRouteTotal.visibility = View.VISIBLE
            } else {
                textRouteTotal.visibility = View.GONE
            }
            headersAdapter.downloadingIds = downloadingIds
            headersAdapter.submitList(item.orders)
        }

        fun updateDownloadingIds(ids: Set<String>) {
            headersAdapter.downloadingIds = ids
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder =
        RouteViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_otros_route, parent, false)
        )

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) =
        holder.bind(getItem(position))

    // Necesario para propagar downloadingIds a los holders ya vinculados
    override fun onBindViewHolder(holder: RouteViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_DOWNLOADING)) {
            // Solo actualizar el estado de descarga sin re-bindear todo
            holder.updateDownloadingIds(downloadingIds)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    companion object {
        private const val PAYLOAD_DOWNLOADING = "payload_downloading"
        private val ROUTE_DIFF = object : DiffUtil.ItemCallback<OtrosRouteUiModel>() {
            override fun areItemsTheSame(a: OtrosRouteUiModel, b: OtrosRouteUiModel) =
                a.routeId == b.routeId
            override fun areContentsTheSame(a: OtrosRouteUiModel, b: OtrosRouteUiModel) =
                a == b
        }
    }
}

/**
 * Swipe horizontal a la izquierda para compartir el ticket en PDF.
 *
 * Solo se habilita el gesto cuando el pedido está [OtrosDownloadUiStatus.COMPLETED];
 * en otros estados [getMovementFlags] retorna 0 desactivando el swipe para esa fila.
 * Replica visualmente el patrón de PedidosPorRutaFragment (fondo rojo + ícono PDF).
 */
private class SharePdfSwipeCallback(
    private val rootView: View,
    private val headersAdapter: OtrosOrderHeaderAdapter,
    private val onSharePdfClick: (OtrosOrderHeaderUiModel) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val bgPaint = Paint().apply { color = "#E53935".toColorInt() }
    private val textPaint = Paint().apply {
        color          = Color.WHITE
        textSize       = 36f
        isAntiAlias    = true
        isFakeBoldText = true
    }
    private val iconDrawable by lazy {
        ContextCompat.getDrawable(rootView.context, R.drawable.ic_pdf)
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return 0
        val item = headersAdapter.currentList.getOrNull(pos) ?: return 0
        // Solo permitir swipe en pedidos descargados (COMPLETED). Si no está descargado
        // no tendría sentido compartir un PDF vacío.
        return if (item.downloadStatus == OtrosDownloadUiStatus.COMPLETED) {
            makeMovementFlags(0, ItemTouchHelper.LEFT)
        } else 0
    }

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.bindingAdapterPosition
        if (pos == RecyclerView.NO_POSITION) return
        val item = headersAdapter.currentList.getOrNull(pos) ?: return
        // Restaurar visualmente la tarjeta inmediatamente — no queremos que desaparezca.
        headersAdapter.notifyItemChanged(pos)
        onSharePdfClick(item)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top

        if (dX < 0) {
            val bgRect = RectF(
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat(),
            )
            c.drawRect(bgRect, bgPaint)

            val iconSize   = (itemHeight * 0.4f).toInt().coerceIn(32, 64)
            val iconMargin = (itemHeight - iconSize) / 2
            val iconLeft   = itemView.right - iconMargin - iconSize
            val iconTop    = itemView.top + (itemHeight - iconSize) / 2
            iconDrawable?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            iconDrawable?.draw(c)

            val swipeWidth = -dX
            if (swipeWidth > iconSize + iconMargin * 2 + 20) {
                val label = "PDF"
                val textX = itemView.right - iconMargin - iconSize / 2f -
                            textPaint.measureText(label) / 2f
                val textY = iconTop + iconSize + textPaint.textSize + 2f
                c.drawText(label, textX, textY, textPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}

/**
 * Adapter de nivel secundario: headers de pedidos dentro de una ruta.
 */
class OtrosOrderHeaderAdapter(
    private val onDownloadClick: (OtrosOrderHeaderUiModel) -> Unit,
    private val onOpenClick: (OtrosOrderHeaderUiModel) -> Unit,
    private val onPrintClick: (OtrosOrderHeaderUiModel) -> Unit = {},
) : ListAdapter<OtrosOrderHeaderUiModel, OtrosOrderHeaderAdapter.HeaderViewHolder>(HEADER_DIFF) {

    var downloadingIds: Set<String> = emptySet()
        set(value) {
            field = value
            val count = itemCount
            if (count > 0) {
                notifyItemRangeChanged(0, count, PAYLOAD_HEADER_DOWNLOADING)
            }
        }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textInicial       = view.findViewById<TextView>(R.id.textOtrosInicial)
        private val textClientName    = view.findViewById<TextView>(R.id.textOtrosClientName)
        private val textCreatedAt     = view.findViewById<TextView>(R.id.textOtrosCreatedAt)
        private val textAddress       = view.findViewById<TextView>(R.id.textOtrosAddress)
        private val textSeller        = view.findViewById<TextView>(R.id.textOtrosSeller)
        private val textItemsCount    = view.findViewById<TextView>(R.id.textOtrosItemsCount)
        private val textStatusLabel   = view.findViewById<TextView>(R.id.textOtrosStatusLabel)
        private val textTotal         = view.findViewById<TextView>(R.id.textOtrosTotal)
        private val btnDownload       = view.findViewById<ImageButton>(R.id.btnOtrosDownload)
        private val progressDownload  = view.findViewById<ProgressBar>(R.id.progressOtrosDownload)
        private val btnPrint          = view.findViewById<ImageButton>(R.id.btnOtrosPrint)

        fun bind(item: OtrosOrderHeaderUiModel) {
            textInicial.text     = PedidosClienteAdapter.initialsOf(item.clientName)
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

            // Tap en tarjeta completa → siempre propaga al Fragment para decidir qué hacer
            itemView.setOnClickListener { onOpenClick(item) }

            bindDownloadState(item)
        }

        /** Actualiza solo la parte visual del estado de descarga (spinner/botón). */
        fun bindDownloadState(item: OtrosOrderHeaderUiModel) {
            val isDownloading = downloadingIds.contains(item.orderId)

            when {
                isDownloading -> {
                    btnDownload.visibility      = View.GONE
                    progressDownload.visibility = View.VISIBLE
                    btnPrint.visibility         = View.GONE
                }
                item.downloadStatus == OtrosDownloadUiStatus.COMPLETED -> {
                    // Ya descargado: el slot de descarga muestra el botón de imprimir.
                    btnDownload.visibility      = View.GONE
                    progressDownload.visibility = View.GONE
                    btnPrint.visibility         = View.VISIBLE
                    btnPrint.setOnClickListener { onPrintClick(item) }
                }
                else -> {
                    // PENDING / FAILED: mostrar botón de descarga
                    btnDownload.visibility      = View.VISIBLE
                    progressDownload.visibility = View.GONE
                    btnPrint.visibility         = View.GONE
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

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_HEADER_DOWNLOADING)) {
            // Solo actualizar la parte del spinner/botón sin re-bindear todo
            getItem(position)?.let { holder.bindDownloadState(it) }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    companion object {
        private const val PAYLOAD_HEADER_DOWNLOADING = "payload_header_downloading"
        private val HEADER_DIFF = object : DiffUtil.ItemCallback<OtrosOrderHeaderUiModel>() {
            override fun areItemsTheSame(a: OtrosOrderHeaderUiModel, b: OtrosOrderHeaderUiModel) =
                a.orderId == b.orderId
            override fun areContentsTheSame(a: OtrosOrderHeaderUiModel, b: OtrosOrderHeaderUiModel) =
                a == b
        }
    }
}

