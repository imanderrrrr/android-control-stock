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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scanner genérico de código de barras (CameraX + ML Kit).
 *
 * Devuelve el resultado vía [Fragment Result API]:
 *   parentFragmentManager.setFragmentResult(requestKey, bundleOf(resultKey to rawValue))
 *
 * Uso:
 *   BarcodeScannerFragment.newInstance(requestKey = "req_scan_barcode")
 *
 * Reutilizable desde cualquier pantalla sin acoplar a ViewModels ni repositorios.
 */
class BarcodeScannerFragment : Fragment() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    /** Debounce: una sola detección por apertura del scanner. */
    private var barcodeDelivered = false

    // ---- Vistas ----
    private lateinit var previewView: PreviewView
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

    // ---- Lifecycle ----

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
        permissionDeniedLayout = view.findViewById(R.id.permissionDeniedLayout)
        scanFrame = view.findViewById(R.id.scanFrame)
        scanHint = view.findViewById(R.id.scanHint)
        toolbar = view.findViewById(R.id.scannerToolbar)

        // Ocultar progress (no lo usamos en modo genérico)
        view.findViewById<View>(R.id.scanProgress)?.visibility = View.GONE

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
    }

    override fun onStop() {
        super.onStop()
        stopCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
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
                analysis.setAnalyzer(cameraExecutor, BarcodeImageAnalyzer { rawValue ->
                    deliverResult(rawValue)
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

    // ---- Resultado ----

    /**
     * Entrega el barcode al fragmento llamante vía Fragment Result API.
     * Debounce: solo se entrega una vez.
     */
    private fun deliverResult(rawValue: String) {
        if (barcodeDelivered) return
        barcodeDelivered = true

        val requestKey = arguments?.getString(ARG_REQUEST_KEY) ?: DEFAULT_REQUEST_KEY
        val resultKey = arguments?.getString(ARG_RESULT_KEY) ?: DEFAULT_RESULT_KEY

        // setFragmentResult y popBackStack deben ejecutarse en el main thread
        view?.post {
            stopCamera()
            parentFragmentManager.setFragmentResult(requestKey, bundleOf(resultKey to rawValue))
            parentFragmentManager.popBackStack()
        }
    }

    // ---- Analizador de imágenes para ML Kit ----

    private class BarcodeImageAnalyzer(
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
        private const val TAG = "BarcodeScanner"

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_RESULT_KEY = "arg_result_key"

        /** Clave por defecto usada en Fragment Result API */
        const val DEFAULT_REQUEST_KEY = "req_scan_barcode"
        const val DEFAULT_RESULT_KEY = "barcode"

        fun newInstance(
            requestKey: String = DEFAULT_REQUEST_KEY,
            resultKey: String = DEFAULT_RESULT_KEY,
        ): BarcodeScannerFragment {
            return BarcodeScannerFragment().apply {
                arguments = bundleOf(
                    ARG_REQUEST_KEY to requestKey,
                    ARG_RESULT_KEY to resultKey,
                )
            }
        }
    }
}



