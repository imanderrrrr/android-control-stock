package com.are.distribuidora.pedido.presentation.catalog

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.model.Product
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class OrderCatalogAdapter : PagingDataAdapter<Product, OrderCatalogAdapter.ProductVH>(DIFF) {

    /** Mapa productId → quantity (desde el ViewModel del carrito) */
    var cartQuantities: Map<String, Int> = emptyMap()
        private set

    /** Callback al tocar la card completa */
    var onProductClicked: ((Product) -> Unit)? = null

    /** Callback al mantener presionada la card */
    var onProductLongPressed: ((Product) -> Unit)? = null

    /** Callback al tocar el botón "+" (primer toque → add) */
    var onAddClicked: ((Product) -> Unit)? = null

    /** Callback al tocar "+" en el stepper (qty > 0) */
    var onIncrementClicked: ((String) -> Unit)? = null

    /** Callback al tocar "-" en el stepper */
    var onDecrementClicked: ((String) -> Unit)? = null

    /** Actualiza el mapa de cantidades y refresca los items afectados. */
    fun updateCartQuantities(newMap: Map<String, Int>) {
        val old = cartQuantities
        cartQuantities = newMap
        // Refrescar solo los items cuya cantidad cambió
        snapshot().items.forEachIndexed { index, product ->
            val id = product.id.value
            if (old[id] != newMap[id]) {
                notifyItemChanged(index, PAYLOAD_QTY)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_catalog_product, parent, false)
        return ProductVH(
            itemView = view,
            onCardClicked  = { product -> onProductClicked?.invoke(product) },
            onCardLongPressed = { product -> onProductLongPressed?.invoke(product) },
            onAddClicked   = { product -> onAddClicked?.invoke(product) },
            onIncrement    = { id      -> onIncrementClicked?.invoke(id) },
            onDecrement    = { id      -> onDecrementClicked?.invoke(id) },
        )
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int) {
        getItem(position)?.let { product ->
            val qty = cartQuantities[product.id.value] ?: 0
            holder.bind(product, qty, animate = false)
        }
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_QTY)) {
            getItem(position)?.let { product ->
                val qty = cartQuantities[product.id.value] ?: 0
                holder.updateQuantity(qty, animate = true)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    class ProductVH(
        itemView: View,
        private val onCardClicked: (Product) -> Unit,
        private val onCardLongPressed: (Product) -> Unit,
        private val onAddClicked: (Product) -> Unit,
        private val onIncrement: (String) -> Unit,
        private val onDecrement: (String) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {

        private val card             = itemView.findViewById<MaterialCardView>(R.id.cardRoot)
        private val image            = itemView.findViewById<ShapeableImageView>(R.id.imageProduct)
        private val placeholder      = itemView.findViewById<ImageView>(R.id.imagePlaceholder)
        private val name             = itemView.findViewById<TextView>(R.id.textName)
        private val price            = itemView.findViewById<TextView>(R.id.textPrice)
        private val buttonAdd        = itemView.findViewById<MaterialButton>(R.id.buttonAdd)
        private val stepperContainer = itemView.findViewById<View>(R.id.stepperContainer)
        private val buttonDecrement  = itemView.findViewById<MaterialButton>(R.id.buttonDecrement)
        private val textQuantity     = itemView.findViewById<TextView>(R.id.textQuantity)
        private val buttonIncrement  = itemView.findViewById<MaterialButton>(R.id.buttonIncrement)

        private var current: Product? = null

        init {
            card.setOnClickListener { current?.let(onCardClicked) }
            card.setOnLongClickListener {
                current?.let(onCardLongPressed)
                true
            }
            buttonAdd.setOnClickListener { current?.let(onAddClicked) }
            buttonIncrement.setOnClickListener { current?.let { onIncrement(it.id.value) } }
            buttonDecrement.setOnClickListener { current?.let { onDecrement(it.id.value) } }
        }

        fun bind(product: Product, qty: Int, animate: Boolean) {
            current = product
            name.text = product.name

            val gt = Locale("es", "GT")
            val nf = NumberFormat.getCurrencyInstance(gt).also {
                it.currency = Currency.getInstance("GTQ")
            }
            price.text = nf.format(product.price.amount)

            bindImage(product)
            updateQuantity(qty, animate)
        }

        fun updateQuantity(qty: Int, animate: Boolean) {
            textQuantity.text = qty.toString()
            if (qty > 0) showStepper(animate) else showAddButton(animate)
        }

        private fun showStepper(animate: Boolean) {
            if (stepperContainer.visibility == View.VISIBLE) return
            if (animate) {
                buttonAdd.animate()
                    .alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(160)
                    .withEndAction {
                        buttonAdd.visibility = View.GONE
                        stepperContainer.alpha = 0f
                        stepperContainer.scaleX = 0.5f
                        stepperContainer.scaleY = 0.5f
                        stepperContainer.visibility = View.VISIBLE
                        stepperContainer.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .start()
                    }.start()
            } else {
                buttonAdd.visibility = View.GONE
                buttonAdd.alpha = 0f
                buttonAdd.scaleX = 0.5f
                buttonAdd.scaleY = 0.5f
                stepperContainer.visibility = View.VISIBLE
                stepperContainer.alpha = 1f
                stepperContainer.scaleX = 1f
                stepperContainer.scaleY = 1f
            }
        }

        private fun showAddButton(animate: Boolean) {
            if (buttonAdd.visibility == View.VISIBLE) return
            if (animate) {
                stepperContainer.animate()
                    .alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(160)
                    .withEndAction {
                        stepperContainer.visibility = View.GONE
                        buttonAdd.alpha = 0f
                        buttonAdd.scaleX = 0.5f
                        buttonAdd.scaleY = 0.5f
                        buttonAdd.visibility = View.VISIBLE
                        buttonAdd.animate()
                            .alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .start()
                    }.start()
            } else {
                stepperContainer.visibility = View.GONE
                stepperContainer.alpha = 0f
                buttonAdd.visibility = View.VISIBLE
                buttonAdd.alpha = 1f
                buttonAdd.scaleX = 1f
                buttonAdd.scaleY = 1f
            }
        }

        private fun bindImage(product: Product) {
            val raw = product.imageUrl?.trim()?.takeIf { it.isNotEmpty() }

            if (raw == null) {
                // Sin imagen: limpiar ImageView y mostrar placeholder
                Glide.with(image).clear(image)
                image.setImageDrawable(null)
                image.visibility = View.INVISIBLE
                placeholder.visibility = View.VISIBLE
                return
            }

            // Con imagen: ocultar placeholder, mostrar ImageView
            placeholder.visibility = View.GONE
            image.visibility = View.VISIBLE

            val request = when {
                raw.startsWith("local://") -> {
                    val path = raw.removePrefix("local://")
                    if (path.isNotBlank()) Glide.with(image).load(File(path))
                    else Glide.with(image).load(R.drawable.ic_image_error)
                }
                raw.startsWith("http://") || raw.startsWith("https://") ->
                    Glide.with(image).load(raw)
                raw.startsWith("content://") || raw.startsWith("file://") ->
                    Glide.with(image).load(raw)
                else -> {
                    // URL desconocida → placeholder
                    Glide.with(image).clear(image)
                    image.visibility = View.INVISIBLE
                    placeholder.visibility = View.VISIBLE
                    return
                }
            }

            request
                .override(300, 300)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean,
                    ): Boolean {
                        // En fallo de carga, volver a mostrar placeholder
                        image.visibility = View.INVISIBLE
                        placeholder.visibility = View.VISIBLE
                        Log.e("ORDER_CATALOG", "Image load failed id=${product.id.value}", e)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean,
                    ): Boolean = false
                })
                .into(image)
        }
    }

    companion object {
        private const val PAYLOAD_QTY = "payload_qty"

        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product) =
                oldItem == newItem
        }
    }
}
