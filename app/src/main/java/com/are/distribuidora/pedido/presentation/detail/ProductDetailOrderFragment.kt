package com.are.distribuidora.pedido.presentation.detail

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.pedido.presentation.common.FlowAnimations
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Detalle de producto en el flujo de creación de pedido.
 * Réplica del diseño Pencil "07": imagen, código/nombre/precio/stock, stepper de
 * cantidad, subtotal en vivo, notas, y barra inferior "Agregar al pedido" (toda
 * la barra es el botón). La cantidad seleccionada vive en [ProductDetailOrderViewModel].
 */
@AndroidEntryPoint
class ProductDetailOrderFragment : Fragment(R.layout.fragment_product_detail_order) {

    private val detailViewModel: ProductDetailOrderViewModel by viewModels()
    private val flowViewModel: CreatePedidoFlowViewModel by activityViewModels()

    /** True mientras el texto del campo de cantidad se fija por código (no por el usuario). */
    private var updatingQtyFromState = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val imageDetail      = view.findViewById<ShapeableImageView>(R.id.imageDetail)
        val imagePlaceholder = view.findViewById<ImageView>(R.id.imagePlaceholderDetail)
        val textCode         = view.findViewById<TextView>(R.id.textDetailCode)
        val textName         = view.findViewById<TextView>(R.id.textDetailName)
        val textPrice        = view.findViewById<TextView>(R.id.textDetailPrice)
        val textStock        = view.findViewById<TextView>(R.id.textDetailStock)
        val stepperMinus     = view.findViewById<MaterialButton>(R.id.stepperMinus)
        val stepperQty       = view.findViewById<EditText>(R.id.stepperQty)
        val stepperPlus      = view.findViewById<MaterialButton>(R.id.stepperPlus)
        val textSubtotalLine = view.findViewById<TextView>(R.id.textDetailSubtotalLine)
        val textSubtotalVal  = view.findViewById<TextView>(R.id.textDetailSubtotalValue)
        val editNotes        = view.findViewById<EditText>(R.id.editNotes)
        val textCartPill     = view.findViewById<TextView>(R.id.textCartPill)
        val addBar           = view.findViewById<View>(R.id.addBar)
        val textAddBarUnits  = view.findViewById<TextView>(R.id.textAddBarUnits)
        val textAddBarTotal  = view.findViewById<TextView>(R.id.textAddBarTotal)

        view.findViewById<View>(R.id.btnBackDetail).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val product   = requireArguments().toProduct()
        detailViewModel.load(product)

        val nf        = buildCurrencyFormat()
        val unitPrice = product.price.amount.toDouble()

        bindImage(product.imageUrl, imageDetail, imagePlaceholder)
        textName.text  = product.name
        textPrice.text = nf.format(product.price.amount)

        // Categoría · código
        val codeParts = listOfNotNull(
            product.category?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale("es", "GT")),
            product.barcode?.trim()?.takeIf { it.isNotBlank() }?.let { "CÓDIGO $it" },
        )
        textCode.text = codeParts.joinToString(" · ")
        textCode.visibility = if (codeParts.isEmpty()) View.GONE else View.VISIBLE

        // Stock
        val stockValue = product.stock.value
        if (stockValue > 0) {
            textStock.text = "STOCK · $stockValue"
        } else {
            textStock.text = getString(R.string.order_no_stock).uppercase(Locale("es", "GT"))
            textStock.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.danger_bg)
            textStock.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger_text))
        }

        editNotes.doAfterTextChanged { detailViewModel.onNotesChanged(it?.toString().orEmpty()) }

        // Stepper (ajusta selectedQty). clearFocus deja que el campo editable
        // vuelva a reflejar el valor del ViewModel (ver colector de selectedQty).
        stepperPlus.setOnClickListener {
            stepperQty.clearFocus()
            detailViewModel.onOtherQtyConfirmed((detailViewModel.selectedQty.value ?: 0) + 1)
        }
        stepperMinus.setOnClickListener {
            stepperQty.clearFocus()
            val current = detailViewModel.selectedQty.value ?: 0
            if (current > 1) detailViewModel.onOtherQtyConfirmed(current - 1)
        }

        // Entrada directa por teclado numérico: al tocar el campo de cantidad se
        // puede escribir cualquier valor (1–999). El guard evita el bucle
        // colector→setText→listener→ViewModel→colector.
        stepperQty.doAfterTextChanged { editable ->
            if (updatingQtyFromState) return@doAfterTextChanged
            val value = editable?.toString()?.trim()?.toIntOrNull() ?: return@doAfterTextChanged
            if (value in 1..999 && value != detailViewModel.selectedQty.value) {
                detailViewModel.onOtherQtyConfirmed(value)
            }
        }
        stepperQty.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                stepperQty.clearFocus()
                hideKeyboard(stepperQty)
                true
            } else {
                false
            }
        }
        stepperQty.setOnFocusChangeListener { _, hasFocus ->
            // Al salir del campo, normaliza el texto al valor real del ViewModel
            // (p. ej. si quedó vacío o con un valor no confirmado).
            if (!hasFocus) {
                setStepperQtyText(stepperQty, (detailViewModel.selectedQty.value ?: 0).toString())
            }
        }

        // Toda la barra inferior es el botón "Agregar al pedido"
        addBar.setOnClickListener { detailViewModel.onAddToCartClicked() }

        // Entrada: la barra "Agregar" sube desde abajo; el contenido del scroll
        // cae en cascada vía android:layoutAnimation. Solo en la primera creación.
        if (savedInstanceState == null) {
            FlowAnimations.revealUp(addBar, delay = 240L, distanceDp = 56f)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Cantidad → stepper, subtotal, barra inferior
                launch {
                    var prevQty: Int? = null
                    detailViewModel.selectedQty.collect { qty ->
                        val q = qty ?: 0
                        // Mientras el usuario escribe (campo enfocado) no se pisa su
                        // entrada; el foco se limpia con +/-/Done para resincronizar.
                        if (!stepperQty.hasFocus()) {
                            setStepperQtyText(stepperQty, q.toString())
                        }
                        // Rebote del número al cambiar (no en la primera emisión).
                        if (prevQty != null && q != prevQty) FlowAnimations.pulse(stepperQty, peak = 1.18f)
                        prevQty = q
                        textAddBarUnits.text  = getString(R.string.order_detail_units, q)
                        val subtotal = unitPrice * q
                        textSubtotalLine.text = "Subtotal · $q × ${nf.format(product.price.amount)}"
                        textSubtotalVal.text  = nf.format(subtotal)
                        textAddBarTotal.text  = nf.format(subtotal)
                    }
                }

                // Pastilla de carrito (cantidad · total) — refleja el carrito existente
                launch {
                    flowViewModel.cartItems.collect { cart ->
                        val count = cart.size
                        val total = cart.values.sumOf { it.subtotal }
                        textCartPill.text = "$count · ${nf.format(total)}"
                    }
                }

                // Eventos: agregar al carrito / error
                launch {
                    detailViewModel.events.collect { event ->
                        when (event) {
                            is ProductDetailOrderViewModel.Event.AddedToCart -> {
                                flowViewModel.addToCart(
                                    product  = product,
                                    quantity = event.qty,
                                    notes    = detailViewModel.notes.value.takeIf { it.isNotBlank() },
                                )
                                parentFragmentManager.popBackStack()
                            }
                            is ProductDetailOrderViewModel.Event.Error ->
                                Snackbar.make(view, event.message, Snackbar.LENGTH_SHORT).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    // ── Imagen con Glide ────────────────────────────────────────────────────────

    private fun bindImage(rawUrl: String?, image: ShapeableImageView, placeholder: ImageView) {
        val raw = rawUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (raw == null) {
            Glide.with(image).clear(image)
            image.visibility = View.INVISIBLE
            placeholder.visibility = View.VISIBLE
            return
        }
        placeholder.visibility = View.GONE
        image.visibility = View.VISIBLE

        val request = when {
            raw.startsWith("local://") -> {
                val path = raw.removePrefix("local://")
                if (path.isNotBlank()) Glide.with(image).load(File(path))
                else { image.visibility = View.INVISIBLE; placeholder.visibility = View.VISIBLE; return }
            }
            raw.startsWith("http://") || raw.startsWith("https://") -> Glide.with(image).load(raw)
            raw.startsWith("content://") || raw.startsWith("file://") -> Glide.with(image).load(raw)
            else -> { image.visibility = View.INVISIBLE; placeholder.visibility = View.VISIBLE; return }
        }

        request
            .override(600, 400)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    image.visibility = View.INVISIBLE
                    placeholder.visibility = View.VISIBLE
                    return true
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean) = false
            })
            .into(image)
    }

    /** Fija el texto de la cantidad sin disparar el listener de edición y deja el cursor al final. */
    private fun setStepperQtyText(field: EditText, text: String) {
        if (field.text.toString() == text) return
        updatingQtyFromState = true
        field.setText(text)
        field.setSelection(field.text.length)
        updatingQtyFromState = false
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun buildCurrencyFormat(): NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }

    // ── Companion: args serialization ──────────────────────────────────────────

    companion object {
        private const val ARG_ID          = "productId"
        private const val ARG_NAME        = "productName"
        private const val ARG_DESC        = "productDesc"
        private const val ARG_CATEGORY    = "productCategory"
        private const val ARG_PRICE       = "productPrice"
        private const val ARG_IMAGE_URL   = "productImageUrl"
        private const val ARG_BARCODE     = "productBarcode"
        private const val ARG_STOCK       = "productStock"
        private const val ARG_CREATED_AT  = "productCreatedAt"
        private const val ARG_UPDATED_AT  = "productUpdatedAt"

        fun newInstance(product: Product): ProductDetailOrderFragment =
            ProductDetailOrderFragment().apply {
                arguments = bundleOf(
                    ARG_ID          to product.id.value,
                    ARG_NAME        to product.name,
                    ARG_DESC        to product.description,
                    ARG_CATEGORY    to product.category,
                    ARG_PRICE       to product.price.amount.toDouble(),
                    ARG_IMAGE_URL   to product.imageUrl,
                    ARG_BARCODE     to product.barcode,
                    ARG_STOCK       to product.stock.value,
                    ARG_CREATED_AT  to product.createdAt,
                    ARG_UPDATED_AT  to product.updatedAt,
                )
            }

        private fun Bundle.toProduct(): Product {
            val id        = getString(ARG_ID, "")
            val name      = getString(ARG_NAME, "")
            val desc      = getString(ARG_DESC)
            val category  = getString(ARG_CATEGORY)
            val price     = getDouble(ARG_PRICE, 0.0)
            val imageUrl  = getString(ARG_IMAGE_URL)
            val barcode   = getString(ARG_BARCODE)
            val stock     = getInt(ARG_STOCK, 0)
            val createdAt = getLong(ARG_CREATED_AT, 0L)
            val updatedAt = getLong(ARG_UPDATED_AT, 0L)
            return Product(
                id          = ProductId.of(id),
                name        = name,
                description = desc,
                category    = category,
                price       = Money.of(BigDecimal.valueOf(price)),
                imageUrl    = imageUrl,
                barcode     = barcode,
                stock       = Quantity.of(stock),
                createdAt   = createdAt,
                updatedAt   = updatedAt,
            )
        }
    }
}
