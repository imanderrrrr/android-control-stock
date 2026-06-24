package com.are.distribuidora.pedido.presentation.catalog

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.core.Logger
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

/**
 * Adapter del catálogo de productos en el flujo de creación de pedido.
 *
 * Diseño (fix de la race "Inconsistency detected. Invalid view holder
 * adapter position"):
 *  - El PagingData transporta SOLO [Product]. La cantidad del carrito NO
 *    forma parte del item paginado.
 *  - `submitData()` tiene una ÚNICA fuente (búsqueda/categoría). El carrito
 *    llega por un canal separado vía [submitCartQuantities], que emite un
 *    `notifyItemChanged(pos, PAYLOAD_QTY)` puntual. Así nunca hay dos
 *    generaciones de PagingData compitiendo con el layout pass del
 *    RecyclerView — que era exactamente la causa del crash (un scrap holder
 *    de la lista filtrada reusado contra la generación de la lista completa).
 *
 * @param logger Inyectado para que los fallos del adapter (ej. Glide image load
 *               failures) alimenten el ring buffer del crash reporter y queden
 *               disponibles en el siguiente reporte de crash si lo hubiera.
 */
class OrderCatalogAdapter(
    private val logger: Logger,
) : PagingDataAdapter<Product, OrderCatalogAdapter.ProductVH>(DIFF) {

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

    /**
     * Cantidades del carrito por productId. Snapshot inmutable que empuja el
     * Fragment vía [submitCartQuantities] por un canal SEPARADO de submitData.
     * El adapter lo lee en bind; nunca forma parte del PagingData.
     */
    private var quantities: Map<String, Int> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_catalog_product, parent, false)
        return ProductVH(
            itemView = view,
            logger = logger,
            onCardClicked  = { product -> onProductClicked?.invoke(product) },
            onCardLongPressed = { product -> onProductLongPressed?.invoke(product) },
            onAddClicked   = { product -> onAddClicked?.invoke(product) },
            onIncrement    = { id      -> onIncrementClicked?.invoke(id) },
            onDecrement    = { id      -> onDecrementClicked?.invoke(id) },
        )
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int) {
        getItem(position)?.let { product ->
            holder.bind(product, quantities[product.id.value] ?: 0, animate = false)
        }
    }

    override fun onBindViewHolder(holder: ProductVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_QTY)) {
            getItem(position)?.let { product ->
                holder.updateQuantity(quantities[product.id.value] ?: 0, animate = true)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /**
     * Actualiza SOLO las cantidades del carrito sin re-disparar `submitData`.
     *
     * Recorre el snapshot presentado actualmente y emite un
     * `notifyItemChanged(pos, PAYLOAD_QTY)` únicamente en los ítems cuya
     * cantidad cambió. Coexiste de forma segura con `submitData` porque:
     *  - `submitData` tiene UNA sola fuente (búsqueda/categoría), así que no
     *    hay dos generaciones de PagingData compitiendo.
     *  - El notify es puntual y acotado a posiciones realmente presentadas
     *    (nunca un rango ni `notifyDataSetChanged`), respetando el itemCount real.
     *  - Ambos canales corren en el main thread, por lo que se serializan.
     */
    fun submitCartQuantities(newQuantities: Map<String, Int>) {
        val old = quantities
        quantities = newQuantities
        snapshot().forEachIndexed { index, product ->
            if (product != null) {
                val oldQty = old[product.id.value] ?: 0
                val newQty = newQuantities[product.id.value] ?: 0
                if (oldQty != newQty) notifyItemChanged(index, PAYLOAD_QTY)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    class ProductVH(
        itemView: View,
        private val logger: Logger,
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
        private val textStock        = itemView.findViewById<TextView>(R.id.textStock)
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

            // Stock disponible = existencias − comprometido
            val ctx = itemView.context
            val available = (product.stock.value - product.comprometido).coerceAtLeast(0)
            if (available > 0) {
                textStock.text = ctx.getString(R.string.order_stock_format, available)
                textStock.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.success_bg)
                textStock.setTextColor(ContextCompat.getColor(ctx, R.color.success_text))
            } else {
                textStock.text = ctx.getString(R.string.order_no_stock)
                textStock.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.danger_bg)
                textStock.setTextColor(ContextCompat.getColor(ctx, R.color.danger_text))
            }

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
            // Prioridad: imageUrl (https remota) → imageLocalUri → sin imagen
            val remoteUrl = product.imageUrl?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val localUri = product.imageLocalUri?.trim()?.takeIf { it.isNotEmpty() }

            // Resolver fuente con prioridad
            when {
                remoteUrl != null -> {
                    placeholder.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    loadWithGlide(product, Glide.with(image).load(remoteUrl))
                    return
                }
                localUri != null -> {
                    placeholder.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    loadWithGlide(product, Glide.with(image).load(File(localUri)))
                    return
                }
                else -> {
                    // Sin imagen: limpiar ImageView y mostrar placeholder
                    Glide.with(image).clear(image)
                    image.setImageDrawable(null)
                    image.visibility = View.INVISIBLE
                    placeholder.visibility = View.VISIBLE
                    return
                }
            }
        }

        private fun loadWithGlide(
            product: Product,
            request: com.bumptech.glide.RequestBuilder<Drawable>,
        ) {
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
                        image.visibility = View.INVISIBLE
                        placeholder.visibility = View.VISIBLE
                        logger.e("ORDER_CATALOG", "Image load failed id=${product.id.value}", e)
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

        /**
         * DiffUtil sobre [Product] — la cantidad del carrito ya NO vive en el
         * item paginado, se aplica vía [submitCartQuantities].
         *  - areItemsTheSame: mismo productId (identidad estable).
         *  - areContentsTheSame: producto idéntico (precio, nombre, imagen…).
         *
         * Sin `getChangePayload` de qty: los cambios de cantidad los notifica
         * [submitCartQuantities] con [PAYLOAD_QTY], no el diff de paginación.
         */
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product) =
                oldItem == newItem
        }
    }
}
