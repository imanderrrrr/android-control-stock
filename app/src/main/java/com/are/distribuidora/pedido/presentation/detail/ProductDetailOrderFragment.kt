package com.are.distribuidora.pedido.presentation.detail

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
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
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@AndroidEntryPoint
class ProductDetailOrderFragment : Fragment(R.layout.fragment_product_detail_order) {

    private val detailViewModel: ProductDetailOrderViewModel by viewModels()
    private val flowViewModel: CreatePedidoFlowViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Vistas ──────────────────────────────────────────────────────────
        val toolbar              = view.findViewById<MaterialToolbar>(R.id.toolbarDetail)
        val imageDetail          = view.findViewById<ShapeableImageView>(R.id.imageDetail)
        val imagePlaceholder     = view.findViewById<ImageView>(R.id.imagePlaceholderDetail)
        val textName             = view.findViewById<TextView>(R.id.textDetailName)
        val textDesc             = view.findViewById<TextView>(R.id.textDetailDescription)
        val textPrice            = view.findViewById<TextView>(R.id.textDetailPrice)
        val chipGroupPresets     = view.findViewById<ChipGroup>(R.id.chipGroupPresets)
        val buttonOtros          = view.findViewById<MaterialButton>(R.id.buttonOtros)
        val buttonAddPreset      = view.findViewById<MaterialButton>(R.id.buttonAddPreset)
        val editNotes            = view.findViewById<TextInputEditText>(R.id.editNotes)
        val buttonAddToCart      = view.findViewById<MaterialButton>(R.id.buttonAddToCart)
        val textTemplatesLabel   = view.findViewById<TextView>(R.id.textTemplatesLabel)
        val scrollTemplates      = view.findViewById<View>(R.id.scrollTemplates)
        val chipGroupTemplates   = view.findViewById<ChipGroup>(R.id.chipGroupTemplates)
        val buttonCreateTemplate = view.findViewById<MaterialButton>(R.id.buttonCreateTemplate)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // ── Reconstruir el Product desde los argumentos ──────────────────────
        val product = requireArguments().toProduct()
        detailViewModel.load(product)

        // ── Imagen ───────────────────────────────────────────────────────────
        bindImage(product.imageUrl, imageDetail, imagePlaceholder)

        // ── Textos fijos ─────────────────────────────────────────────────────
        val nf = buildCurrencyFormat()
        textName.text  = product.name
        textPrice.text = nf.format(product.price.amount)

        val desc = product.description?.trim()
        if (!desc.isNullOrBlank()) {
            textDesc.text = desc
            textDesc.visibility = View.VISIBLE
        } else {
            textDesc.visibility = View.GONE
        }

        // ── Notes input ───────────────────────────────────────────────────────
        editNotes.doAfterTextChanged { detailViewModel.onNotesChanged(it?.toString().orEmpty()) }

        // ── Botones ───────────────────────────────────────────────────────────
        buttonOtros.setOnClickListener { detailViewModel.onOtherClicked() }
        buttonAddPreset.setOnClickListener { detailViewModel.onAddPresetClicked() }
        buttonAddToCart.setOnClickListener { detailViewModel.onAddToCartClicked() }
        buttonCreateTemplate.setOnClickListener { detailViewModel.onCreateTemplateClicked() }

        // ── Observadores ─────────────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Presets → reconstruir chips
                launch {
                    detailViewModel.presets.collect { presets ->
                        val currentSelected = detailViewModel.selectedQty.value
                        rebuildChips(chipGroupPresets, presets, currentSelected)
                    }
                }

                // Cantidad seleccionada → habilitar botón + sync chips
                launch {
                    detailViewModel.selectedQty.collect { qty ->
                        buttonAddToCart.isEnabled = qty != null && qty > 0
                        syncChipSelection(chipGroupPresets, qty)
                    }
                }

                // Plantillas de detalle
                launch {
                    detailViewModel.templates.collect { templates ->
                        val currentSelected = detailViewModel.selectedTemplate.value
                        rebuildTemplateChips(chipGroupTemplates, templates, currentSelected)
                        val hasTemplates = templates.isNotEmpty()
                        textTemplatesLabel.visibility = if (hasTemplates) View.VISIBLE else View.GONE
                        scrollTemplates.visibility = if (hasTemplates) View.VISIBLE else View.GONE
                    }
                }

                // Plantilla seleccionada → sync chips de plantillas + actualizar input
                launch {
                    detailViewModel.selectedTemplate.collect { selected ->
                        syncTemplateChipSelection(chipGroupTemplates, selected)
                        // Si el notes actual no coincide con el selected (cuando se deselecciona),
                        // actualizar input evitando loop: solo si viene de selección nueva
                        if (selected != null && editNotes.text?.toString() != selected) {
                            editNotes.setText(selected)
                            editNotes.setSelection(selected.length)
                        }
                    }
                }

                // Eventos one-shot
                launch {
                    detailViewModel.events.collect { event ->
                        when (event) {
                            is ProductDetailOrderViewModel.Event.ShowOtherDialog ->
                                showQtyDialog(
                                    title   = getString(R.string.detail_otros_dialog_title),
                                    onConfirm = { detailViewModel.onOtherQtyConfirmed(it) }
                                )

                            is ProductDetailOrderViewModel.Event.ShowAddPresetDialog ->
                                showQtyDialog(
                                    title   = getString(R.string.detail_add_preset_dialog_title),
                                    onConfirm = { detailViewModel.onAddPresetConfirmed(it) }
                                )

                            is ProductDetailOrderViewModel.Event.ShowCreateTemplateDialog ->
                                showCreateTemplateDialog()

                            is ProductDetailOrderViewModel.Event.AddedToCart -> {
                                flowViewModel.addToCart(
                                    product  = product,
                                    quantity = event.qty,
                                    notes    = detailViewModel.notes.value.takeIf { it.isNotBlank() },
                                )
                                Snackbar.make(
                                    view,
                                    getString(R.string.detail_added_snackbar, event.qty),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                parentFragmentManager.popBackStack()
                            }

                            is ProductDetailOrderViewModel.Event.Error ->
                                Snackbar.make(view, event.message, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // ── Chips ──────────────────────────────────────────────────────────────────

    private fun rebuildChips(group: ChipGroup, presets: List<Int>, selectedQty: Int?) {
        group.removeAllViews()
        presets.forEach { qty ->
            val chip = Chip(requireContext()).apply {
                text = qty.toString()
                isCheckable = true
                isChecked = qty == selectedQty
                setOnClickListener { detailViewModel.onPresetClicked(qty) }
            }
            group.addView(chip)
        }
    }

    private fun syncChipSelection(group: ChipGroup, selectedQty: Int?) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            val chipQty = chip.text.toString().toIntOrNull()
            chip.isChecked = chipQty != null && chipQty == selectedQty
        }
    }

    // ── Diálogo numérico ────────────────────────────────────────────────────────

    private fun showQtyDialog(title: String, onConfirm: (Int) -> Unit) {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.detail_qty_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(getString(R.string.detail_dialog_ok)) { _, _ ->
                val qty = input.text?.toString()?.trim()?.toIntOrNull() ?: 0
                onConfirm(qty)
            }
            .setNegativeButton(getString(R.string.detail_dialog_cancel), null)
            .show()
    }

    // ── Chips de plantillas ────────────────────────────────────────────────────

    private fun rebuildTemplateChips(group: ChipGroup, templates: List<String>, selected: String?) {
        group.removeAllViews()
        templates.forEach { text ->
            val chip = Chip(requireContext()).apply {
                this.text = text
                isCheckable = true
                isChecked = text == selected
                setOnClickListener { detailViewModel.onTemplateSelected(text) }
                setOnLongClickListener {
                    showDeleteTemplateDialog(text)
                    true
                }
            }
            group.addView(chip)
        }
    }

    private fun syncTemplateChipSelection(group: ChipGroup, selected: String?) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.text.toString() == selected
        }
    }

    // ── Diálogo crear plantilla ────────────────────────────────────────────────

    private fun showCreateTemplateDialog() {
        val layout = TextInputLayout(requireContext(), null,
            com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.detail_template_hint)
            setPadding(48, 16, 48, 8)
        }
        val input = TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 3
        }
        layout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.detail_create_template_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.detail_dialog_ok)) { _, _ ->
                val text = input.text?.toString().orEmpty()
                detailViewModel.onTemplateCreateConfirmed(text)
            }
            .setNegativeButton(getString(R.string.detail_dialog_cancel), null)
            .show()
    }

    private fun showDeleteTemplateDialog(text: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.detail_delete_template_title))
            .setMessage(getString(R.string.detail_delete_template_message, text))
            .setPositiveButton(getString(R.string.detail_delete_template_confirm)) { _, _ ->
                detailViewModel.onTemplateDeleteConfirmed(text)
            }
            .setNegativeButton(getString(R.string.detail_dialog_cancel), null)
            .show()
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
            .override(300, 300)
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




