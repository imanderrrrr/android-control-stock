package com.are.distribuidora.pedido.presentation.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R

class PedidosRouteAdapter(
    private val onRouteClick: (RouteOrderSummaryUiModel) -> Unit,
) : ListAdapter<RouteOrderSummaryUiModel, PedidosRouteAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textName     = view.findViewById<TextView>(R.id.textRouteName)
        private val textSubtitle = view.findViewById<TextView>(R.id.textRouteSubtitle)
        private val badgeCount   = view.findViewById<TextView>(R.id.badgeCount)
        private val textTotal    = view.findViewById<TextView>(R.id.textRouteTotal)

        fun bind(item: RouteOrderSummaryUiModel) {
            textName.text     = item.routeName
            textSubtitle.text = itemView.context.getString(R.string.pedidos_route_subtitle, item.pedidoCount)
            badgeCount.text   = item.pedidoCount.toString()
            textTotal.text    = item.totalFormatted
            itemView.setOnClickListener { onRouteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_pedido_route, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RouteOrderSummaryUiModel>() {
            override fun areItemsTheSame(a: RouteOrderSummaryUiModel, b: RouteOrderSummaryUiModel) =
                a.routeId == b.routeId
            override fun areContentsTheSame(a: RouteOrderSummaryUiModel, b: RouteOrderSummaryUiModel) =
                a == b
        }
    }
}

