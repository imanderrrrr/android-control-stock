package com.are.distribuidora.pedido.presentation.edit

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
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

/**
 * Adapter para la lista editable de ítems del pedido.
 * Muestra la imagen del producto (Glide), nombre, precio unitario, subtotal y
 * controles +/- y eliminar.
 */
class EditPedidoAdapter(
    private val onIncrement: (EditItemUiModel) -> Unit,
    private val onDecrement: (EditItemUiModel) -> Unit,
    private val onDelete:    (EditItemUiModel) -> Unit,
) : ListAdapter<EditItemUiModel, EditPedidoAdapter.ViewHolder>(DIFF) {

    @Suppress("DEPRECATION")
    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageProduct    = view.findViewById<ShapeableImageView>(R.id.imageEditProduct)
        private val imagePlaceholder = view.findViewById<ImageView>(R.id.imageEditPlaceholder)
        private val textNombre      = view.findViewById<TextView>(R.id.textEditItemNombre)
        private val textPrecio      = view.findViewById<TextView>(R.id.textEditItemPrecio)
        private val textSubtotal    = view.findViewById<TextView>(R.id.textEditItemSubtotal)
        private val textQty         = view.findViewById<TextView>(R.id.textEditQty)
        private val btnDecr         = view.findViewById<ImageButton>(R.id.btnDecrQty)
        private val btnIncr         = view.findViewById<ImageButton>(R.id.btnIncrQty)
        private val btnDelete       = view.findViewById<ImageButton>(R.id.btnDeleteEditItem)

        private var current: EditItemUiModel? = null

        init {
            btnIncr.setOnClickListener   { current?.let { onIncrement(it) } }
            btnDecr.setOnClickListener   { current?.let { onDecrement(it) } }
            btnDelete.setOnClickListener { current?.let { onDelete(it) } }
        }

        fun bind(item: EditItemUiModel) {
            current          = item
            textNombre.text  = item.nombre
            textPrecio.text  = buildString {
                append(currencyFormat.format(item.precioUnitario))
                append(" c/u")
            }
            textQty.text      = item.cantidad.toString()
            textSubtotal.text = currencyFormat.format(item.subtotal)
            bindImage(item.imageUrl)
        }

        private fun bindImage(rawUrl: String?) {
            val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() }

            if (raw == null) {
                Glide.with(imageProduct).clear(imageProduct)
                imageProduct.setImageDrawable(null)
                imageProduct.visibility     = View.INVISIBLE
                imagePlaceholder.visibility = View.VISIBLE
                return
            }

            imagePlaceholder.visibility = View.GONE
            imageProduct.visibility     = View.VISIBLE

            val request = when {
                raw.startsWith("local://") -> {
                    val path = raw.removePrefix("local://")
                    if (path.isNotBlank()) Glide.with(imageProduct).load(File(path))
                    else {
                        imageProduct.visibility     = View.INVISIBLE
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
                    imageProduct.visibility     = View.INVISIBLE
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
                        imageProduct.visibility     = View.INVISIBLE
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_edit_pedido, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EditItemUiModel>() {
            override fun areItemsTheSame(a: EditItemUiModel, b: EditItemUiModel) =
                a.localKey == b.localKey
            override fun areContentsTheSame(a: EditItemUiModel, b: EditItemUiModel) =
                a == b
        }
    }
}
