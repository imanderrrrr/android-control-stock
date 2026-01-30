package com.are.distribuidora.presentation.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.model.Product
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class InventoryAdapter(
    private val onItemClicked: ((Product) -> Unit)? = null,
) : ListAdapter<Product, InventoryAdapter.ProductVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_product, parent, false)
        return ProductVH(view, onItemClicked)
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int) {
        holder.bind(getItem(position))
    }

    class ProductVH(
        itemView: View,
        private val onItemClicked: ((Product) -> Unit)?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image = itemView.findViewById<ImageView>(R.id.productImage)
        private val name = itemView.findViewById<TextView>(R.id.productName)
        private val price = itemView.findViewById<TextView>(R.id.productPrice)
        private val stock = itemView.findViewById<TextView>(R.id.productStock)

        fun bind(item: Product) {
            Log.d("Inventory", "Binding product=${item.name}")

            itemView.setOnClickListener {
                onItemClicked?.invoke(item)
            }

            name.text = item.name

            val gt = Locale("es", "GT")
            val nf = NumberFormat.getCurrencyInstance(gt)
            nf.currency = Currency.getInstance("GTQ")
            val priceText = nf.format(item.price.amount)
            price.text = itemView.context.getString(R.string.inventory_price, priceText)

            stock.text = itemView.context.getString(R.string.inventory_stock, item.stock.value)

            Glide.with(image)
                .load(null as Any?)
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .into(image)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
                oldItem == newItem
        }
    }
}
