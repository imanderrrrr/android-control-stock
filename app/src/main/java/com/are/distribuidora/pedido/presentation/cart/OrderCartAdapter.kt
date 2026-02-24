package com.are.distribuidora.pedido.presentation.cart

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.create.CartItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.imageview.ShapeableImageView
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Adapter del carrito con soporte para:
 * - Deslizar DERECHA → editar cantidad
 * - Deslizar IZQUIERDA → aplicar descuento
 * - Long-press → eliminar producto
 */
class OrderCartAdapter(
    private val onEditQuantity: (CartItem) -> Unit,
    private val onEditDiscount: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit,
) : ListAdapter<CartItem, OrderCartAdapter.CartVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_cart, parent, false)
        return CartVH(view)
    }

    override fun onBindViewHolder(holder: CartVH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnLongClickListener {
            onRemove(item)
            true
        }
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────

    class CartVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val card: View = itemView.findViewById(R.id.cartItemCard)

        private val textName        = itemView.findViewById<TextView>(R.id.textCartItemName)
        private val textSubtotal    = itemView.findViewById<TextView>(R.id.textCartItemSubtotal)
        private val textUnitPrice   = itemView.findViewById<TextView>(R.id.textCartItemUnitPrice)
        private val textNotes       = itemView.findViewById<TextView>(R.id.textCartItemNotes)
        private val badgeQty        = itemView.findViewById<TextView>(R.id.chipCartQty)
        private val badgeDiscount   = itemView.findViewById<TextView>(R.id.chipCartDiscount)
        private val imageProduct    = itemView.findViewById<ShapeableImageView>(R.id.imageCartProduct)
        private val imagePlaceholder = itemView.findViewById<ImageView>(R.id.imageCartPlaceholder)

        fun bind(item: CartItem) {
            val nf = buildCurrencyFormat()

            textName.text     = item.name
            textSubtotal.text = nf.format(item.subtotal)
            badgeQty.text     = "× ${item.quantity}"

            // Precio base + badge de descuento (solo si hay descuento activo)
            if (item.hasDiscount) {
                textUnitPrice.text = "${nf.format(item.subtotalBase)} base"
                textUnitPrice.visibility = View.VISIBLE
                badgeDiscount.text = "-${item.discountPercent.toInt()}%"
                badgeDiscount.visibility = View.VISIBLE
            } else {
                textUnitPrice.visibility = View.GONE
                badgeDiscount.visibility  = View.GONE
            }

            // Notas
            val notes = item.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                textNotes.text = notes
                textNotes.visibility = View.VISIBLE
            } else {
                textNotes.visibility = View.GONE
            }

            bindImage(item.imageUrl)
        }

        private fun bindImage(rawUrl: String?) {
            val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() }

            if (raw == null) {
                Glide.with(imageProduct).clear(imageProduct)
                imageProduct.setImageDrawable(null)
                imageProduct.visibility = View.INVISIBLE
                imagePlaceholder.visibility = View.VISIBLE
                return
            }

            imagePlaceholder.visibility = View.GONE
            imageProduct.visibility = View.VISIBLE

            val request = when {
                raw.startsWith("local://") -> {
                    val path = raw.removePrefix("local://")
                    if (path.isNotBlank()) Glide.with(imageProduct).load(File(path))
                    else {
                        imageProduct.visibility = View.INVISIBLE
                        imagePlaceholder.visibility = View.VISIBLE
                        return
                    }
                }
                raw.startsWith("http://") || raw.startsWith("https://") ->
                    Glide.with(imageProduct).load(raw)
                raw.startsWith("content://") || raw.startsWith("file://") ->
                    Glide.with(imageProduct).load(raw)
                else -> {
                    Glide.with(imageProduct).clear(imageProduct)
                    imageProduct.visibility = View.INVISIBLE
                    imagePlaceholder.visibility = View.VISIBLE
                    return
                }
            }

            request
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?,
                        target: Target<Drawable>, isFirstResource: Boolean,
                    ): Boolean {
                        imageProduct.visibility = View.INVISIBLE
                        imagePlaceholder.visibility = View.VISIBLE
                        return true
                    }
                    override fun onResourceReady(
                        resource: Drawable, model: Any, target: Target<Drawable>,
                        dataSource: DataSource, isFirstResource: Boolean,
                    ): Boolean = false
                })
                .into(imageProduct)
        }

        private fun buildCurrencyFormat(): NumberFormat {
            val gt = Locale("es", "GT")
            return NumberFormat.getCurrencyInstance(gt).also {
                it.currency = Currency.getInstance("GTQ")
            }
        }
    }

    // ── SwipeCallback ───────────────────────────────────────────────────────

    /**
     * ItemTouchHelper que maneja swipe izquierda/derecha sobre el CARD interior,
     * sin mover el FrameLayout raíz (los fondos ya están en el layout).
     */
    inner class SwipeCallback : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {

        // Colores para los fondos de swipe
        private val paintEdit     = android.graphics.Paint().apply { color = 0xFF4CAF50.toInt() }
        private val paintDiscount = android.graphics.Paint().apply { color = 0xFFFF6F00.toInt() }
        private val paintText     = android.graphics.Paint().apply {
            color     = android.graphics.Color.WHITE
            textSize  = 38f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        private val cornerRadius = 16f * 3   // ~48px, igual al card (16dp × density ≈ gestionado por RectF)

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ) = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_ID.toInt()) return
            val item = getItem(position)
            notifyItemChanged(position)   // snap back visual
            when (direction) {
                ItemTouchHelper.RIGHT -> onEditQuantity(item)
                ItemTouchHelper.LEFT  -> onEditDiscount(item)
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float, dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            val vh   = viewHolder as? CartVH ?: return
            val card = vh.card

            if (dX != 0f) {
                val iv     = viewHolder.itemView
                val top    = iv.top.toFloat()
                val bottom = iv.bottom.toFloat()
                val cornerPx = cornerRadius * recyclerView.resources.displayMetrics.density / 3f

                if (dX > 0) {
                    // ── Deslizar derecha: fondo VERDE a la izquierda del card ──
                    val rect = android.graphics.RectF(iv.left.toFloat(), top, iv.left + dX, bottom)
                    c.drawRoundRect(rect, cornerPx, cornerPx, paintEdit)

                    // Ícono + texto "Editar"
                    val label  = "✎  Editar"
                    val textY  = top + (bottom - top) / 2f - (paintText.descent() + paintText.ascent()) / 2f
                    c.drawText(label, iv.left + 48f, textY, paintText)

                } else {
                    // ── Deslizar izquierda: fondo NARANJA a la derecha del card ──
                    val rect = android.graphics.RectF(iv.right + dX, top, iv.right.toFloat(), bottom)
                    c.drawRoundRect(rect, cornerPx, cornerPx, paintDiscount)

                    // Texto "Descuento" alineado a la derecha
                    val label  = "Descuento  %"
                    val textW  = paintText.measureText(label)
                    val textY  = top + (bottom - top) / 2f - (paintText.descent() + paintText.ascent()) / 2f
                    c.drawText(label, iv.right - textW - 48f, textY, paintText)
                }
            }

            // Mover solo el card
            card.translationX = dX
            card.elevation    = if (abs(dX) > 10f) 8f else 2f
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ) {
            super.clearView(recyclerView, viewHolder)
            val vh = viewHolder as? CartVH ?: return
            vh.card.translationX = 0f
            vh.card.elevation    = 2f
        }

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.30f
        override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue * 1.2f
    }

    fun attachToRecyclerView(rv: RecyclerView) {
        ItemTouchHelper(SwipeCallback()).attachToRecyclerView(rv)
    }

    // ── DiffCallback ────────────────────────────────────────────────────────

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) =
                oldItem.productId == newItem.productId
            override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) =
                oldItem == newItem
        }
    }
}
