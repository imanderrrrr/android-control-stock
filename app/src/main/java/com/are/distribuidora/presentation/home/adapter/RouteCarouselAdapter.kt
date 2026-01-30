package com.are.distribuidora.presentation.home.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.route.domain.model.Route

class RouteCarouselAdapter(
    private val onRouteClick: (Route) -> Unit
) : ListAdapter<Route, RouteCarouselAdapter.RouteViewHolder>(RouteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_card, parent, false)
        return RouteViewHolder(view, onRouteClick)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RouteViewHolder(
        itemView: View,
        val onClick: (Route) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val nameView: TextView = itemView.findViewById(R.id.routeName)
        private val dayView: TextView = itemView.findViewById(R.id.routeDay)
        private val countView: TextView = itemView.findViewById(R.id.clientCount)
        private var currentRoute: Route? = null

        init {
            itemView.setOnClickListener {
                currentRoute?.let { onClick(it) }
            }
        }

        fun bind(route: Route) {
            currentRoute = route
            nameView.text = route.name
            dayView.text = getDayName(itemView.context, route.deliveryDay)
            countView.text = itemView.context.getString(R.string.inventory_stock, route.clientsCount).replace("Stock:", "Clientes:") // Reusing string format or creating new if strict
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
