package com.are.distribuidora.presentation.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.model.ProductUiModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class InventoryAdapter : PagingDataAdapter<ProductUiModel, InventoryAdapter.ProductVH>(DIFF) {

    var onEditClick: ((ProductUiModel) -> Unit)? = null
    var onDeleteClick: ((ProductUiModel) -> Unit)? = null
    var onProductClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_product, parent, false)
        return ProductVH(view, onEditClick, onDeleteClick, onProductClick)
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int) {
        getItem(position)?.let { item ->
            holder.bind(item)
        }
    }

    class ProductVH(
        itemView: View,
        private val onEditClick: ((ProductUiModel) -> Unit)?,
        private val onDeleteClick: ((ProductUiModel) -> Unit)?,
        private val onProductClick: ((String) -> Unit)?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val image = itemView.findViewById<ImageView>(R.id.productImage)
        private val name = itemView.findViewById<TextView>(R.id.productName)
        private val price = itemView.findViewById<TextView>(R.id.productPrice)
        private val stock = itemView.findViewById<TextView>(R.id.productStock)
        private val comprometido = itemView.findViewById<TextView>(R.id.productComprometido)
        private val menuButton = itemView.findViewById<ImageButton>(R.id.menuButton)

        // New Indicators
        private val syncIndicator = itemView.findViewById<TextView>(R.id.syncIndicatorText)
        private val activeIndicator = itemView.findViewById<TextView>(R.id.activeIndicatorText)

        private var currentProduct: ProductUiModel? = null

        init {
            // Click en tarjeta -> detalle
            itemView.setOnClickListener {
                currentProduct?.let { model ->
                    onProductClick?.invoke(model.product.id.value)
                }
            }

            // Configurar click en menuButton (3 puntos) en lugar de la tarjeta completa
            menuButton.setOnClickListener { view ->
                currentProduct?.let { product ->
                    showPopupMenu(view, product)
                }
            }
        }

        fun bind(item: ProductUiModel) {
            val product = item.product
            currentProduct = item

            name.text = product.name

            val gt = Locale("es", "GT")
            val nf = NumberFormat.getCurrencyInstance(gt)
            nf.currency = Currency.getInstance("GTQ")
            val priceText = nf.format(product.price.amount)
            price.text = itemView.context.getString(R.string.inventory_price, priceText)

            stock.text = itemView.context.getString(R.string.inventory_stock, product.stock.value)

            if (product.comprometido > 0) {
                comprometido.visibility = View.VISIBLE
                comprometido.text = itemView.context.getString(
                    R.string.inventory_comprometido, product.comprometido
                )
            } else {
                comprometido.visibility = View.GONE
            }

            // Bind New Statuses
            syncIndicator.text = item.syncIndicatorText
            activeIndicator.text = item.activeIndicatorText
            // Logic for active color/visibility if needed (Client uses emoji so string is enough)

            // 1. Extract image sources with priority: imageUrl (remote https) -> imageLocalUri -> imageUrl (legacy)
            val remoteUrl = product.imageUrl?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val localUri = product.imageLocalUri?.trim()?.takeIf { it.isNotEmpty() }
            val raw = product.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

            // 2. Resolve source with priority
            when {
                remoteUrl != null -> {
                    Glide.with(image).load(remoteUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(image)
                    return
                }
                localUri != null -> {
                    Glide.with(image).load(File(localUri))
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(image)
                    return
                }
                raw == null -> {
                    Glide.with(image).clear(image)
                    image.setImageResource(R.drawable.ic_image_placeholder)
                    return
                }
            }

            // 3. Legacy imageUrl fallback
            val loadRequest = when {
                raw.startsWith("local://") -> {
                    // Extract path: local:///data/... -> /data/...
                    // We can use ProductImageUrl helper if available or manual string manipulation
                    // User requested specific logic: "extrae path y usa File(path)"
                    val path = raw.removePrefix("local://")
                    if (path.isNotBlank()) {
                         Glide.with(image).load(File(path))
                    } else {
                        // Fallback validity check
                        Glide.with(image).load(R.drawable.ic_image_error)
                    }
                }
                raw.startsWith("http://") ||
                raw.startsWith("https://") -> {
                     Glide.with(image).load(raw)
                }
                raw.startsWith("content://") ||
                raw.startsWith("file://") -> {
                     Glide.with(image).load(raw)
                }
                else -> {
                    // Unrecognized scheme -> treat as error/placeholder
                    Glide.with(image).clear(image)
                    image.setImageResource(R.drawable.ic_image_placeholder)
                    return
                }
            }

            // 4. Apply Configuration & Listener
            loadRequest
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        android.util.Log.e("InventoryImage", "Load failed for id=${product.id.value} raw=$raw", e)
                        return false // Allow Glide to handle error drawable
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
                .into(image)
        }

        private fun showPopupMenu(view: View, product: ProductUiModel) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.menu_product_item, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEditClick?.invoke(product)
                        true
                    }
                    R.id.action_delete -> {
                        // Invoca callback para confirmar/borrar en Fragment
                        onDeleteClick?.invoke(product)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProductUiModel>() {
            override fun areItemsTheSame(oldItem: ProductUiModel, newItem: ProductUiModel): Boolean =
                oldItem.product.id == newItem.product.id

            override fun areContentsTheSame(oldItem: ProductUiModel, newItem: ProductUiModel): Boolean =
                oldItem == newItem
        }
    }
}
