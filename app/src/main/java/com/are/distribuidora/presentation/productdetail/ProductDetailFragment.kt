package com.are.distribuidora.presentation.productdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.core.images.ProductImageUrl
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.FragmentComponent
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductDetailFragment : Fragment() {

    private lateinit var viewModel: ProductDetailViewModel

    @EntryPoint
    @InstallIn(FragmentComponent::class)
    interface ProductDetailEntryPoint {
        fun productDetailViewModelFactory(): ProductDetailViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()

        val entryPoint = EntryPointAccessors.fromFragment(this, ProductDetailEntryPoint::class.java)
        viewModel = entryPoint.productDetailViewModelFactory().create(productId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_product_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val errorText = view.findViewById<TextView>(R.id.errorText)

        val image = view.findViewById<ImageView>(R.id.productImage)
        val name = view.findViewById<TextView>(R.id.productName)

        val chipStatus = view.findViewById<Chip>(R.id.chipStatus)
        val chipSync = view.findViewById<Chip>(R.id.chipSync)

        val priceValue = view.findViewById<TextView>(R.id.tvPriceValue)
        val stockValue = view.findViewById<TextView>(R.id.tvStockValue)

        val itemDescription = view.findViewById<View>(R.id.itemDescription)
        val itemCategory = view.findViewById<View>(R.id.itemCategory)
        val itemBarcode = view.findViewById<View>(R.id.itemBarcode)
        val itemUnit = view.findViewById<View>(R.id.itemUnit)

        val tvDescLabel = itemDescription.findViewById<TextView>(R.id.tvLabel)
        val tvDescValue = itemDescription.findViewById<TextView>(R.id.tvValue)

        val tvCatLabel = itemCategory.findViewById<TextView>(R.id.tvLabel)
        val tvCatValue = itemCategory.findViewById<TextView>(R.id.tvValue)

        val tvBarcodeLabel = itemBarcode.findViewById<TextView>(R.id.tvLabel)
        val tvBarcodeValue = itemBarcode.findViewById<TextView>(R.id.tvValue)

        val tvUnitLabel = itemUnit.findViewById<TextView>(R.id.tvLabel)
        val tvUnitValue = itemUnit.findViewById<TextView>(R.id.tvValue)

        // Labels (nunca hardcode)
        tvDescLabel.text = getString(R.string.label_description)
        tvCatLabel.text = getString(R.string.label_category)
        tvBarcodeLabel.text = getString(R.string.label_barcode)
        tvUnitLabel.text = getString(R.string.label_unit)

        chipStatus.isClickable = false
        chipStatus.isCheckable = false
        chipSync.isClickable = false
        chipSync.isCheckable = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        ProductDetailUiState.Loading -> {
                            progress.visibility = View.VISIBLE
                            errorText.visibility = View.GONE
                        }

                        is ProductDetailUiState.Error -> {
                            progress.visibility = View.GONE
                            errorText.visibility = View.VISIBLE
                            errorText.text = when (state.kind) {
                                ProductDetailUiState.ErrorKind.NOT_FOUND -> getString(R.string.product_detail_not_found)
                            }

                            // Limpieza de UI principal
                            name.text = ""
                            chipStatus.visibility = View.GONE
                            chipSync.visibility = View.GONE
                            priceValue.text = ""
                            stockValue.text = ""
                            tvDescValue.text = getString(R.string.value_not_available)
                            tvCatValue.text = getString(R.string.value_not_available)
                            tvBarcodeValue.text = getString(R.string.value_not_available)
                            tvUnitValue.text = getString(R.string.value_not_available)

                            Glide.with(image).clear(image)
                            image.setImageResource(R.drawable.ic_image_placeholder)
                        }

                        is ProductDetailUiState.Success -> {
                            progress.visibility = View.GONE
                            errorText.visibility = View.GONE

                            val product = state.product

                            name.text = product.name

                            // Chips: estado
                            chipStatus.visibility = View.VISIBLE
                            if (product.isActive) {
                                chipStatus.text = getString(R.string.chip_status_active)
                            } else {
                                chipStatus.text = getString(R.string.chip_status_inactive)
                            }
                            chipStatus.isChipIconVisible = false

                            // Chips: sync (reutiliza labels existentes)
                            chipSync.visibility = View.VISIBLE
                            chipSync.text = when (state.syncState) {
                                null -> getString(R.string.product_detail_sync_unknown)
                                com.are.distribuidora.domain.core.SyncState.SYNCED   -> getString(R.string.product_detail_sync_synced)
                                com.are.distribuidora.domain.core.SyncState.SYNCING  -> getString(R.string.product_detail_sync_syncing)
                                com.are.distribuidora.domain.core.SyncState.PENDING  -> getString(R.string.product_detail_sync_pending)
                                com.are.distribuidora.domain.core.SyncState.FAILED   -> getString(R.string.product_detail_sync_failed)
                                com.are.distribuidora.domain.core.SyncState.CONFLICT -> getString(R.string.product_detail_sync_conflict)
                            }
                            chipSync.isChipIconVisible = false

                            // Resumen destacado
                            priceValue.text = "Q" + product.price.amount.toPlainString()
                            stockValue.text = product.stock.value.toString()

                            // Detalles (label/value). Valores con fallback a resources.
                            tvDescValue.text = product.description?.trim().takeIf { !it.isNullOrBlank() }
                                ?: getString(R.string.value_no_description)

                            tvCatValue.text = product.category?.trim().takeIf { !it.isNullOrBlank() }
                                ?: getString(R.string.value_not_available)

                            tvBarcodeValue.text = product.barcode?.trim().takeIf { !it.isNullOrBlank() }
                                ?: getString(R.string.value_no_barcode)

                            // Unidad/medida: si el dominio no la expone, mostramos "—"
                            val unitValue = try {
                                val unit = product::class.java.getDeclaredField("unit").apply { isAccessible = true }.get(product)
                                unit?.toString()?.trim().takeIf { !it.isNullOrBlank() }
                            } catch (_: Throwable) {
                                null
                            }
                            tvUnitValue.text = unitValue ?: getString(R.string.value_not_available)

                            bindImage(image, product.imageUrl, product.imageLocalUri)
                        }
                    }
                }
            }
        }
    }

    private fun bindImage(imageView: ImageView, imageUrl: String?, localUri: String?) {
        // Priority: imageUrl (remote https) -> localUri -> placeholder
        val remoteUrl = imageUrl?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val effectiveLocal = localUri?.trim()?.takeIf { it.isNotEmpty() }

        when {
            remoteUrl != null -> {
                Glide.with(imageView).load(remoteUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .override(300, 300)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView)
            }
            effectiveLocal != null -> {
                Glide.with(imageView).load(File(effectiveLocal))
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .override(300, 300)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView)
            }
            else -> {
                Glide.with(imageView).clear(imageView)
                imageView.setImageResource(R.drawable.ic_image_placeholder)
            }
        }
    }

    companion object {
        private const val ARG_PRODUCT_ID = "productId"

        fun newInstance(productId: String): ProductDetailFragment {
            return ProductDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRODUCT_ID, productId)
                }
            }
        }
    }
}
