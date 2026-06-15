package com.are.distribuidora.route.presentation

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.route.domain.model.Route
import com.google.android.material.card.MaterialCardView

class SelectRouteAdapter(
    private val onRouteClick: (Route) -> Unit
) : ListAdapter<Route, SelectRouteAdapter.RouteViewHolder>(RouteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_card_bosque, parent, false)
        return RouteViewHolder(view, onRouteClick)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        // La primera ruta se resalta como "sugerida / última usada" (afordancia visual;
        // cuando el modelo tenga "última usada" real se puede cambiar el criterio).
        holder.bind(getItem(position), isPrimary = position == 0)
    }

    class RouteViewHolder(
        itemView: View,
        val onClick: (Route) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.routeCard)
        private val nameView: TextView = itemView.findViewById(R.id.routeName)
        private val subtitleView: TextView = itemView.findViewById(R.id.routeSubtitle)
        private val clientsView: TextView = itemView.findViewById(R.id.statClients)
        private val badgeView: TextView = itemView.findViewById(R.id.badgeUltima)
        private var currentRoute: Route? = null

        init {
            itemView.setOnClickListener {
                currentRoute?.let { onClick(it) }
            }
        }

        fun bind(route: Route, isPrimary: Boolean) {
            val ctx = itemView.context
            currentRoute = route

            nameView.text = route.name
            subtitleView.text = "Entrega · " + getDayName(ctx, route.deliveryDay)
            clientsView.text =
                if (route.clientsCount == 1) "1 cliente" else "${route.clientsCount} clientes"

            badgeView.visibility = if (isPrimary) View.VISIBLE else View.GONE
            val density = ctx.resources.displayMetrics.density
            if (isPrimary) {
                card.strokeColor = ContextCompat.getColor(ctx, R.color.brand_primary)
                card.strokeWidth = (1.5f * density).toInt()
            } else {
                card.strokeColor = ContextCompat.getColor(ctx, R.color.border_subtle)
                card.strokeWidth = (1f * density).toInt()
            }
        }

        private fun getDayName(context: Context, day: Int): String {
            return when (day) {
                1 -> context.getString(R.string.day_monday)
                2 -> context.getString(R.string.day_tuesday)
                3 -> context.getString(R.string.day_wednesday)
                4 -> context.getString(R.string.day_thursday)
                5 -> context.getString(R.string.day_friday)
                6 -> context.getString(R.string.day_saturday)
                7 -> context.getString(R.string.day_sunday)
                else -> ""
            }
        }
    }

    class RouteDiffCallback : DiffUtil.ItemCallback<Route>() {
        override fun areItemsTheSame(oldItem: Route, newItem: Route): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Route, newItem: Route): Boolean {
            return oldItem == newItem
        }
    }
}
