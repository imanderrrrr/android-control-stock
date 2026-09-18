package com.are.distribuidora.pedido.presentation.catalog

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.core.glide.GlideFailures
import com.are.distribuidora.core.images.DisplayableProductImage
import com.are.distribuidora.core.images.ImageLoadFailureMemo
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
 * Diseño (fix definitivo del crash "Inconsistency detected", tercera aparición):
 *  - El PagingData transporta SOLO [Product]. La cantidad del carrito NO forma
 *    parte del item paginado.
 *  - El differ de Paging es el ÚNICO que notifica al RecyclerView. Este adapter
 *    no llama a `notify*` en ningún caso: ni `notifyItemChanged`, ni rangos, ni
 *    `notifyDataSetChanged`.
 *  - Las cantidades del carrito llegan por [submitCartQuantities] y se pintan
 *    escribiendo DIRECTO sobre los ViewHolders visibles ([ProductVH.bindQuantityOnly]),
 *    sin tocar la contabilidad de posiciones del RecyclerView. Las filas que aún
 *    no están enlazadas toman la cantidad de [quantities] en el bind normal.
 *
 * Por qué los dos intentos anteriores no bastaron:
 *  - `1519ad1` metió el carrito DENTRO del PagingData (combine + submitData). Dos
 *    orígenes de emisión alimentaban un `collectLatest`, que cancelaba diffs a
 *    medio aplicar.
 *  - `500e7bb` sacó el carrito del PagingData, pero lo dejó notificando a mano con
 *    `notifyItemChanged(pos, PAYLOAD_QTY)`. Seguían siendo DOS fuentes escribiendo
 *    sobre la misma contabilidad de posiciones: RecyclerView no consume esas
 *    operaciones cuando se emiten, sino de forma diferida en el layout, así que al
 *    cambiar el tamaño de la lista de golpe (vaciar la búsqueda: ~3 ítems → ~450)
 *    los offsets del differ y los del notify manual divergían y reventaba en
 *    `onLayout`. El comentario que decía que era seguro "porque ambos canales corren
 *    en el main thread" era falso por esa razón.
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

    /**
     * RecyclerView al que está enlazado el adapter. Se usa SOLO para recorrer los
     * ViewHolders visibles en [submitCartQuantities] y escribirles la cantidad
     * directamente. Nunca para notificar cambios.
     */
    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (this.recyclerView === recyclerView) this.recyclerView = null
    }

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

    /**
     * Actualiza SOLO las cantidades del carrito, SIN notificar al RecyclerView.
     *
     * El differ de Paging es el único dueño de las notificaciones; este método no
     * puede emitir `notify*` sin volver a abrir la race que crashea en `onLayout`
     * (ver el KDoc de la clase). En su lugar:
     *  - guarda el mapa, que es lo que leerán las filas que se enlacen después, y
     *  - recorre los ViewHolders YA visibles y escribe la cantidad directamente en
     *    sus vistas, identificándolos por el producto que tienen enlazado — nunca
     *    por posición de adapter, para no tocar la contabilidad de Paging ni
     *    disparar la carga de páginas.
     */
    fun submitCartQuantities(newQuantities: Map<String, Int>) {
        quantities = newQuantities

        val rv = recyclerView ?: return
        // Durante el layout, cambiar la visibilidad del stepper dispararía un
        // requestLayout anidado. Se difiere al siguiente frame; el mapa ya quedó
        // guardado arriba, así que el repaso diferido lee el estado más reciente.
        if (rv.isComputingLayout) rv.post { repaintVisibleQuantities(rv) }
        else repaintVisibleQuantities(rv)
    }

    /**
     * Escribe la cantidad actual en cada ViewHolder visible cuyo número cambió.
     *
     * La referencia de "lo viejo" es lo que el propio holder tiene pintado
     * ([ProductVH.boundQuantity]), no un mapa anterior: es el estado real de la
     * pantalla y sigue siendo correcto aunque el repaso se haya diferido y en medio
     * hayan entrado más cambios.
     */
    private fun repaintVisibleQuantities(rv: RecyclerView) {
        rv.children.forEach { child ->
            val holder = rv.getChildViewHolder(child) as? ProductVH ?: return@forEach
            val productId = holder.boundProductId ?: return@forEach
            val newQty = quantities[productId] ?: 0
            if (holder.boundQuantity != newQty) holder.bindQuantityOnly(newQty, animate = true)
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
        private val subtitle         = itemView.findViewById<TextView>(R.id.textSubtitle)
        private val price            = itemView.findViewById<TextView>(R.id.textPrice)
        private val textStock        = itemView.findViewById<TextView>(R.id.textStock)
        private val buttonAdd        = itemView.findViewById<MaterialButton>(R.id.buttonAdd)
        private val stepperContainer = itemView.findViewById<View>(R.id.stepperContainer)
        private val buttonDecrement  = itemView.findViewById<MaterialButton>(R.id.buttonDecrement)
        private val textQuantity     = itemView.findViewById<TextView>(R.id.textQuantity)
        private val buttonIncrement  = itemView.findViewById<MaterialButton>(R.id.buttonIncrement)

        private var current: Product? = null
        private var currentQty: Int = 0

        /** Producto actualmente enlazado, o null si el holder aún no se enlazó. */
        val boundProductId: String? get() = current?.id?.value

        /** Última cantidad pintada en este holder. */
        val boundQuantity: Int get() = currentQty

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

            // Subtítulo: categoría · código
            val ctx = itemView.context
            val subtitleParts = listOfNotNull(
                product.category?.trim()?.takeIf { it.isNotBlank() },
                product.barcode?.trim()?.takeIf { it.isNotBlank() },
            )
            subtitle.text = subtitleParts.joinToString(" · ")
            subtitle.visibility = if (subtitleParts.isEmpty()) View.GONE else View.VISIBLE

            // 4.1: el stock es INFORMACIÓN, no un bloqueo. Se muestra siempre el número; en rojo
            // si es cero o negativo (un negativo significa "entregado antes del vale de entrada").
            // Nunca se impide vender: la venta real manda y el libro de movimientos la explica.
            val stock = product.stock.value
            textStock.text = "STOCK $stock"
            if (stock > 0) {
                textStock.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.brand_soft)
                textStock.setTextColor(ContextCompat.getColor(ctx, R.color.success_text))
            } else {
                textStock.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.danger_bg)
                textStock.setTextColor(ContextCompat.getColor(ctx, R.color.danger_text))
            }

            bindImage(product)
            bindQuantityOnly(qty, animate)
        }

        /**
         * Pinta SOLO la cantidad y el estado del stepper. Único sitio donde viven
         * esas reglas de presentación: lo reutilizan el bind completo y la
         * actualización directa desde [OrderCatalogAdapter.submitCartQuantities].
         */
        fun bindQuantityOnly(qty: Int, animate: Boolean) {
            currentQty = qty
            textQuantity.text = qty.toString()
            // Borde verde de marca cuando el producto está en el carrito (como en Pencil).
            card.setStrokeColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (qty > 0) R.color.brand_primary else R.color.border_subtle,
                )
            )
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
            // Prioridad: imageUrl (https remota utilizable) → imageLocalUri → sin imagen.
            // `remoteUrlOrNull` descarta de una vez las URLs que no pueden cargar (host
            // muerto de Drive, o ya falló con 4xx en esta sesión), así que la fila pinta el
            // placeholder SIN pedir nada a la red.
            val remoteUrl = DisplayableProductImage.remoteUrlOrNull(product.imageUrl)
            val localUri = product.imageLocalUri?.trim()?.takeIf { it.isNotEmpty() }

            // Resolver fuente con prioridad
            when {
                remoteUrl != null -> {
                    placeholder.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    loadWithGlide(product, remoteUrl, Glide.with(image).load(remoteUrl))
                    return
                }
                localUri != null -> {
                    placeholder.visibility = View.GONE
                    image.visibility = View.VISIBLE
                    loadWithGlide(product, localUri, Glide.with(image).load(File(localUri)))
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
            source: String,
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
                        // Un 404 no se arregla reintentando: se anota para no volver a
                        // pedirla en cada bind. Un fallo de red sí se reintenta.
                        if (GlideFailures.isPermanent(e)) {
                            ImageLoadFailureMemo.rememberPermanentFailure(source)
                        }
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
        /**
         * DiffUtil sobre [Product] — la cantidad del carrito ya NO vive en el
         * item paginado, se aplica vía [submitCartQuantities].
         *  - areItemsTheSame: mismo productId (identidad estable).
         *  - areContentsTheSame: producto idéntico (precio, nombre, imagen…).
         *
         * Sin `getChangePayload`: los cambios de cantidad NO se notifican, los
         * pinta [submitCartQuantities] directamente sobre los ViewHolders visibles.
         */
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(oldItem: Product, newItem: Product) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Product, newItem: Product) =
                oldItem == newItem
        }
    }
}
