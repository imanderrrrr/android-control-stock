package com.are.distribuidora.presentation.product

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "AddStockScanner"

@AndroidEntryPoint
class AddStockScannerFragment : Fragment() {

    private val viewModel: AddStockViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    // ---- Vistas ----
    private lateinit var previewView: PreviewView
    private lateinit var scanProgress: CircularProgressIndicator
    private lateinit var permissionDeniedLayout: LinearLayout
    private lateinit var scanFrame: View
    private lateinit var scanHint: TextView
    private lateinit var toolbar: MaterialToolbar

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_add_stock_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.previewView)
        scanProgress = view.findViewById(R.id.scanProgress)
        permissionDeniedLayout = view.findViewById(R.id.permissionDeniedLayout)
        scanFrame = view.findViewById(R.id.scanFrame)
        scanHint = view.findViewById(R.id.scanHint)
        toolbar = view.findViewById(R.id.scannerToolbar)

        cameraExecutor = Executors.newSingleThreadExecutor()

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<MaterialButton>(R.id.btnOpenSettings).setOnClickListener {
            openAppSettings()
        }
        view.findViewById<MaterialButton>(R.id.btnGoBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        checkCameraPermission()
        observeViewModel()
    }

    // ---- Permisos ----

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }

            shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA) -> {
                showPermissionRationale()
            }

            else -> {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.scanner_title)
            .setMessage(R.string.scanner_permission_rationale)
            .setPositiveButton("Dar permiso") { _, _ ->
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
            .setNegativeButton(R.string.scanner_go_back) { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .show()
    }

    private fun showPermissionDenied() {
        previewView.visibility = View.GONE
        scanFrame.visibility = View.GONE
        scanHint.visibility = View.GONE
        permissionDeniedLayout.visibility = View.VISIBLE
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).also { intent ->
            intent.data = Uri.fromParts("package", requireContext().packageName, null)
            startActivity(intent)
        }
    }

    // ---- Cámara ----

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Error al obtener CameraProvider", e)
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer { rawValue ->
                    // Llega en background thread; delegar a ViewModel (thread-safe)
                    viewModel.onBarcodeScanned(rawValue)
                })
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar cámara", e)
        }
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    // ---- Observar ViewModel ----

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AddStockUiState.Scanning -> {
                                scanProgress.visibility = View.GONE
                            }
                            is AddStockUiState.Loading -> {
                                stopCamera()
                                scanProgress.visibility = View.VISIBLE
                            }
                            is AddStockUiState.ProductFound -> {
                                scanProgress.visibility = View.GONE
                                showAddStockDialog(state)
                            }
                            is AddStockUiState.ProductNotFound -> {
                                scanProgress.visibility = View.GONE
                                showProductNotFoundDialog(state.barcode)
                            }
                            is AddStockUiState.StockUpdated -> {
                                // El evento de éxito lo maneja el SharedFlow
                            }
                            is AddStockUiState.Error -> {
                                scanProgress.visibility = View.GONE
                                Snackbar.make(requireView(), state.message, Snackbar.LENGTH_LONG).show()
                            }
                            else -> Unit
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddStockEvent.StockAddedSuccess -> {
                                val msg = getString(R.string.add_stock_success, event.delta)
                                parentFragmentManager.popBackStack()
                                // El Snackbar se mostrará desde InventoryFragment si hay result listener,
                                // o mostramos uno rápido aquí antes de salir:
                                view?.let { v ->
                                    Snackbar.make(v, msg, Snackbar.LENGTH_SHORT).show()
                                }
                            }
                            is AddStockEvent.ShowError -> {
                                Snackbar.make(requireView(), event.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Diálogos ----

    private fun showAddStockDialog(state: AddStockUiState.ProductFound) {
        val product = state.product

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_stock, null)

        val productNameText = dialogView.findViewById<TextView>(R.id.dialogProductName)
        val productDetailsText = dialogView.findViewById<TextView>(R.id.dialogProductDetails)
        val quantityInputLayout = dialogView.findViewById<TextInputLayout>(R.id.quantityInputLayout)
        val quantityInput = dialogView.findViewById<TextInputEditText>(R.id.quantityInput)

        productNameText.text = product.name
        val details = buildString {
            product.barcode?.let { append("Código: $it") }
            product.category?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            append("\nStock actual: ${product.stock.value}")
            append(" · Precio: Q${String.format(Locale.US, "%.2f", product.price.amount)}")
        }
        productDetailsText.text = details

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_stock_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.add_stock_confirm, null) // se sobreescribe abajo
            .setNegativeButton(R.string.add_stock_cancel) { d, _ ->
                d.dismiss()
                viewModel.resumeScanning()
                startCamera()
            }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = quantityInput.text?.toString().orEmpty()
                val quantity = text.toIntOrNull()

                when {
                    text.isBlank() || quantity == null -> {
                        quantityInputLayout.error = getString(R.string.add_stock_invalid_quantity)
                    }
                    quantity <= 0 -> {
                        quantityInputLayout.error = getString(R.string.add_stock_invalid_quantity)
                    }
                    quantity > 99_999 -> {
                        quantityInputLayout.error = "Cantidad demasiado alta"
                    }
                    else -> {
                        quantityInputLayout.error = null
                        dialog.dismiss()
                        viewModel.addStock(
                            productId = product.id.value,
                            productName = product.name,
                            delta = quantity,
                        )
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showProductNotFoundDialog(barcode: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.product_not_found_title)
            .setMessage(getString(R.string.product_not_found_message, barcode))
            .setPositiveButton(R.string.product_not_found_create) { _, _ ->
                navigateToAddProduct(barcode)
            }
            .setNegativeButton(R.string.product_not_found_cancel) { d, _ ->
                d.dismiss()
                viewModel.resumeScanning()
                startCamera()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToAddProduct(barcode: String) {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                AddProductFragment.newInstanceWithBarcode(barcode),
            )
            .addToBackStack(null)
            .commit()
    }

    // ---- Ciclo de vida ----

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    // ---- Analizador de imágenes para ML Kit ----

    private class BarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit,
    ) : ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees,
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val barcode = barcodes.firstOrNull { it.rawValue != null }
                    barcode?.rawValue?.let { rawValue ->
                        onBarcodeDetected(rawValue)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Fallo al procesar frame", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    companion object {
        fun newInstance(): AddStockScannerFragment = AddStockScannerFragment()
    }
}
