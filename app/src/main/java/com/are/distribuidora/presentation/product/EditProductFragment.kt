package com.are.distribuidora.presentation.product

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.core.images.ProductImageUrl
import com.are.distribuidora.domain.model.Product
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProductFragment : Fragment() {

    private val viewModel: EditProductViewModel by viewModels()
    private var productId: String = ""

    /** Barcode escaneado pendiente de aplicar al campo cuando la vista se recree. */
    private var pendingScannedBarcode: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        // Preview inmediato
        view?.findViewById<TextView>(R.id.imageErrorText)?.visibility = View.GONE
        view?.findViewById<ImageView>(R.id.productImagePreview)?.let { imageView ->
            Glide.with(imageView).load(uri).into(imageView)
        }

        // Persistir en internal storage con nombre basado en productId
        try {
            val file = ProductImageLocalStore.saveToInternalStorage(
                context = requireContext(),
                sourceUri = uri,
                productId = productId
            )
            viewModel.onImageSavedToInternalStorage(file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al guardar imagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()

        // Registrar listener con lifecycle del Fragment (no viewLifecycleOwner)
        // para que sobreviva al replace/popBackStack del scanner.
        parentFragmentManager.setFragmentResultListener(
            BarcodeScannerFragment.DEFAULT_REQUEST_KEY,
            this
        ) { _, bundle ->
            val barcode = bundle.getString(BarcodeScannerFragment.DEFAULT_RESULT_KEY)
            if (!barcode.isNullOrBlank()) {
                pendingScannedBarcode = barcode
                viewModel.onBarcodeChanged(barcode)
                // Si la vista ya existe, aplicar inmediatamente
                view?.findViewById<TextInputEditText>(R.id.inputBarcode)?.setText(barcode)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_edit_product, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val scrollView = view.findViewById<View>(R.id.scrollView)
        val nameInput = view.findViewById<TextInputEditText>(R.id.inputName)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.inputDescription)
        val categoryInput = view.findViewById<TextInputEditText>(R.id.inputCategory)
        val priceInput = view.findViewById<TextInputEditText>(R.id.inputPrice)
        val stockInput = view.findViewById<TextInputEditText>(R.id.inputStock)
        val barcodeInput = view.findViewById<TextInputEditText>(R.id.inputBarcode)
        val activeSwitch = view.findViewById<SwitchMaterial>(R.id.switchActive)
        val saveButton = view.findViewById<MaterialButton>(R.id.buttonSave)
        val imagePreview = view.findViewById<ImageView>(R.id.productImagePreview)
        val pickImageButton = view.findViewById<MaterialButton>(R.id.pickProductImageButton)
        val imageErrorText = view.findViewById<TextView>(R.id.imageErrorText)
        val scanBarcodeButton = view.findViewById<MaterialButton>(R.id.btnScanBarcode)

        toolbar.title = "Editar Producto"
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Aplicar barcode escaneado pendiente (si volvemos del scanner)
        pendingScannedBarcode?.let { barcode ->
            barcodeInput.setText(barcode)
            pendingScannedBarcode = null
        }

        scanBarcodeButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    BarcodeScannerFragment.newInstance()
                )
                .addToBackStack(null)
                .commit()
        }

        // Cargar producto
        viewModel.load(productId)

        // Preview de imagen existente
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is EditProductViewModel.EditProductUiState.Success -> {
                                progressBar.visibility = View.GONE
                                scrollView.visibility = View.VISIBLE
                                populateFields(
                                    state.product,
                                    nameInput,
                                    descriptionInput,
                                    categoryInput,
                                    priceInput,
                                    stockInput,
                                    barcodeInput,
                                    activeSwitch
                                )

                                // Prioridad: imageUrl (remota https) -> imageLocalUri -> placeholder
                                when {
                                    state.product.imageUrl?.startsWith("http") == true ->
                                        Glide.with(imagePreview).load(state.product.imageUrl).into(imagePreview)
                                    !state.product.imageLocalUri.isNullOrBlank() ->
                                        Glide.with(imagePreview).load(java.io.File(state.product.imageLocalUri)).into(imagePreview)
                                    ProductImageUrl.isLocal(state.product.imageUrl) -> {
                                        val path = ProductImageUrl.localPathOrNull(state.product.imageUrl)
                                        Glide.with(imagePreview).load(java.io.File(path ?: "")).into(imagePreview)
                                    }
                                    else -> Glide.with(imagePreview).load(android.R.drawable.ic_menu_gallery).into(imagePreview)
                                }
                            }
                            is EditProductViewModel.EditProductUiState.Loading -> {
                                progressBar.visibility = View.VISIBLE
                                scrollView.visibility = View.GONE
                            }
                            is EditProductViewModel.EditProductUiState.Error -> {
                                progressBar.visibility = View.GONE
                                scrollView.visibility = View.VISIBLE
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.isSaving.collect { isSaving ->
                        saveButton.isEnabled = !isSaving
                        saveButton.text = if (isSaving) "Guardando..." else "Guardar Cambios"
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is EditProductViewModel.Event.Success -> {
                                Toast.makeText(context, "Producto actualizado correctamente", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            }
                            is EditProductViewModel.Event.Error -> {
                                if (event.message.contains("imagen", ignoreCase = true)) {
                                    imageErrorText.visibility = View.VISIBLE
                                }
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        pickImageButton.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        // Setup save button
        saveButton.setOnClickListener {
            // Si el producto no tiene imagen previa (ni remota ni local://) y el usuario no elige una nueva, bloquear.
            val uiState = viewModel.uiState.value
            if (uiState is EditProductViewModel.EditProductUiState.Success) {
                val product = uiState.product
                val hasExisting = product.imageUrl?.startsWith("http") == true ||
                        !product.imageLocalUri.isNullOrBlank() ||
                        ProductImageUrl.isRemoteHttp(product.imageUrl) ||
                        ProductImageUrl.isLocal(product.imageUrl)
                val hasSelected = !viewModel.localImageAbsolutePath.value.isNullOrBlank()
                if (!hasExisting && !hasSelected) {
                    imageErrorText.visibility = View.VISIBLE
                    Toast.makeText(context, "La imagen es obligatoria", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }
            imageErrorText.visibility = View.GONE

            val name = nameInput.text?.toString().orEmpty()
            val description = descriptionInput.text?.toString()
            val category = categoryInput.text?.toString()
            val price = priceInput.text?.toString().orEmpty()
            val stock = stockInput.text?.toString().orEmpty()
            val barcode = barcodeInput.text?.toString()

            viewModel.save(
                name = name,
                description = description,
                category = category,
                priceText = price,
                stockText = stock,
                barcode = barcode,
                isActive = activeSwitch.isChecked
            )
        }
    }

    private fun populateFields(
        product: Product,
        nameInput: TextInputEditText,
        descriptionInput: TextInputEditText,
        categoryInput: TextInputEditText,
        priceInput: TextInputEditText,
        stockInput: TextInputEditText,
        barcodeInput: TextInputEditText,
        activeSwitch: SwitchMaterial
    ) {
        // Solo poblar si los campos están vacíos para evitar sobrescribir ediciones del usuario
        if (nameInput.text.toString() != product.name) {
            nameInput.setText(product.name)
        }

        val description = product.description.orEmpty()
        if (descriptionInput.text.toString() != description) {
            descriptionInput.setText(description)
        }

        val category = product.category.orEmpty()
        if (categoryInput.text.toString() != category) {
            categoryInput.setText(category)
        }

        val price = product.price.amount.toString()
        if (priceInput.text.toString() != price) {
            priceInput.setText(price)
        }

        val stock = product.stock.value.toString()
        if (stockInput.text.toString() != stock) {
            stockInput.setText(stock)
        }

        // Si hay un barcode escaneado en el ViewModel, usarlo; si no, usar el del producto.
        val scannedBarcode = viewModel.barcode.value
        val barcode = if (!scannedBarcode.isNullOrBlank()) scannedBarcode else product.barcode.orEmpty()
        if (barcodeInput.text.toString() != barcode) {
            barcodeInput.setText(barcode)
        }

        if (activeSwitch.isChecked != product.isActive) {
            activeSwitch.isChecked = product.isActive
        }
    }

    companion object {
        private const val ARG_PRODUCT_ID = "productId"

        fun newInstance(productId: String): EditProductFragment {
            return EditProductFragment().apply {
                arguments = bundleOf(ARG_PRODUCT_ID to productId)
            }
        }
    }
}
