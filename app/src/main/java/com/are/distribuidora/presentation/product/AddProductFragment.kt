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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddProductFragment : Fragment() {

    private val viewModel: AddProductViewModel by viewModels()

    private var selectedContentUri: Uri? = null

    /** Barcode escaneado pendiente de aplicar al campo cuando la vista se recree. */
    private var pendingScannedBarcode: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        selectedContentUri = uri
        // Preview inmediato (content://)
        view?.findViewById<TextView>(R.id.imageErrorText)?.visibility = View.GONE
        view?.findViewById<ImageView>(R.id.productImagePreview)?.let { imageView ->
            Glide.with(imageView).load(uri).into(imageView)
        }

        // Persistir en internal storage de forma permanente.
        try {
            val productIdForFile = viewModel.getNewProductId()
            val file = ProductImageLocalStore.saveToInternalStorage(
                context = requireContext(),
                sourceUri = uri,
                productId = productIdForFile
            )
            viewModel.onImageSavedToInternalStorage(file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al guardar imagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        return inflater.inflate(R.layout.fragment_add_product, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val scrollView = view.findViewById<View>(R.id.scrollView)

        val imagePreview = view.findViewById<ImageView>(R.id.productImagePreview)
        val pickImageButton = view.findViewById<MaterialButton>(R.id.pickProductImageButton)
        val imageErrorText = view.findViewById<TextView>(R.id.imageErrorText)

        val nameInput = view.findViewById<TextInputEditText>(R.id.inputName)
        val descriptionInput = view.findViewById<TextInputEditText>(R.id.inputDescription)
        val categoryInput = view.findViewById<TextInputEditText>(R.id.inputCategory)
        val priceInput = view.findViewById<TextInputEditText>(R.id.inputPrice)
        val stockInput = view.findViewById<TextInputEditText>(R.id.inputStock)
        val barcodeInput = view.findViewById<TextInputEditText>(R.id.inputBarcode)
        val activeSwitch = view.findViewById<SwitchMaterial>(R.id.switchActive)
        val saveButton = view.findViewById<MaterialButton>(R.id.buttonSave)
        val scanBarcodeButton = view.findViewById<MaterialButton>(R.id.btnScanBarcode)

        toolbar.setTitle(R.string.add_product_title)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Pre-rellenar barcode si viene del flujo "Agregar stock → producto no encontrado"
        arguments?.getString("arg_barcode")?.let { barcode ->
            if (barcode.isNotBlank()) barcodeInput.setText(barcode)
        }

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

        // Placeholder preview
        Glide.with(imagePreview).load(R.drawable.ic_add_centered).into(imagePreview)

        pickImageButton.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        activeSwitch.isChecked = true

        saveButton.setOnClickListener {
            // UI-level check visual
            if (viewModel.localImageAbsolutePath.value.isNullOrBlank()) {
                imageErrorText.visibility = View.VISIBLE
                return@setOnClickListener
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { isSaving ->
                        progressBar.visibility = if (isSaving) View.VISIBLE else View.GONE
                        scrollView.visibility = if (isSaving) View.GONE else View.VISIBLE

                        saveButton.isEnabled = !isSaving
                        saveButton.text = if (isSaving) {
                            getString(R.string.add_product_saving)
                        } else {
                            getString(R.string.add_product_save_button)
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddProductViewModel.Event.Success -> {
                                Toast.makeText(context, R.string.add_product_success, Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            }

                            is AddProductViewModel.Event.Error -> {
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
    }

    companion object {
        private const val ARG_BARCODE = "arg_barcode"

        fun newInstance(): AddProductFragment = AddProductFragment()

        /**
         * Crea la pantalla de "Agregar producto" con el código de barras pre-cargado.
         * Usado desde el flujo "Agregar stock" cuando el producto no existe.
         */
        fun newInstanceWithBarcode(barcode: String): AddProductFragment =
            AddProductFragment().apply {
                arguments = android.os.Bundle().apply {
                    putString(ARG_BARCODE, barcode)
                }
            }
    }
}
