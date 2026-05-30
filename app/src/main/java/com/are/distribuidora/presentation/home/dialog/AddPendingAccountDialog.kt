package com.are.distribuidora.presentation.home.dialog

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.ClientPickerItem
import com.are.distribuidora.presentation.home.ClientsViewModel
import com.are.distribuidora.route.domain.model.Route
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@AndroidEntryPoint
class AddPendingAccountDialog : DialogFragment() {

    private val viewModel: ClientsViewModel by viewModels({ requireParentFragment() })

    private var selectedRoute: Route? = null
    private var selectedClient: ClientPickerItem? = null
    private var selectedDueDateMillis: Long? = null
    private var photoUri: Uri? = null
    private var capturedPhotoUri: Uri? = null
    /** Path absoluto del archivo de imagen ya copiado al filesDir. Se llena cuando el usuario elige foto. */
    private var copiedLocalFilePath: String? = null

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("es", "GT")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedPhotoUri != null) {
            photoUri = capturedPhotoUri
            // Copiar inmediatamente al filesDir mientras el Fragment está activo
            copiedLocalFilePath = copyPhotoToFilesDir(capturedPhotoUri!!)
            dialog?.findViewById<ShapeableImageView>(R.id.imagePreview)?.let { preview ->
                preview.visibility = View.VISIBLE
                preview.setImageURI(photoUri)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            // Copiar inmediatamente al filesDir mientras el Fragment está activo y tiene permisos
            copiedLocalFilePath = copyPhotoToFilesDir(uri)
            dialog?.findViewById<ShapeableImageView>(R.id.imagePreview)?.let { preview ->
                preview.visibility = View.VISIBLE
                preview.setImageURI(photoUri)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraIntent()
        } else {
            Toast.makeText(requireContext(), "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copia la imagen seleccionada/tomada al filesDir de la app (almacenamiento permanente).
     * Debe llamarse mientras el Fragment está activo para tener acceso al ContentResolver.
     * Retorna el path absoluto del archivo copiado, o null si falla.
     */
    private fun copyPhotoToFilesDir(sourceUri: Uri): String? {
        return try {
            val dir = File(requireContext().filesDir, "invoices")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "${UUID.randomUUID()}.jpg")
            requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                Log.d(TAG, "Photo copied to filesDir: ${destFile.absolutePath} (${destFile.length()} bytes)")
                destFile.absolutePath
            } else {
                Log.e(TAG, "Copied file is empty or missing: ${destFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying photo to filesDir", e)
            null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // FIX CRASH 1: Usar MaterialComponents.DayNight.Dialog.Alert como contexto
        // para que TextInputLayout resuelva TextAppearance correctamente.
        val themedContext = android.view.ContextThemeWrapper(
            requireContext(),
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert
        )
        val themedInflater = layoutInflater.cloneInContext(themedContext)
        val view = themedInflater.inflate(R.layout.dialog_add_pending_account, null)

        setupViews(view)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog
        )
            .setView(view)
            .create()

        return dialog
    }

    // FIX CRASH 2: Mover observación de lifecycle a onStart() donde el Fragment
    // ya pasó onCreateView/onCreateDialog y lifecycleScope con repeatOnLifecycle
    // puede funcionar sin intentar acceder viewLifecycleOwner antes de tiempo.
    override fun onStart() {
        super.onStart()

        // Limpiar clientes stale de un diálogo anterior
        viewModel.clearClientsForRoute()

        // Observar rutas reactivamente (evita lista vacía si aún no cargaron)
        lifecycleScope.launch {
            viewModel.routes.collect { routes ->
                val spinnerRoute = dialog?.findViewById<AutoCompleteTextView>(R.id.spinnerRoute)
                    ?: return@collect
                val routeAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    routes.map { it.name }
                )
                spinnerRoute.setAdapter(routeAdapter)
                spinnerRoute.setOnItemClickListener { _, _, position, _ ->
                    if (position < routes.size) {
                        selectedRoute = routes[position]
                        selectedClient = null
                        dialog?.findViewById<AutoCompleteTextView>(R.id.spinnerClient)
                            ?.setText("", false)
                        viewModel.loadClientsForRoute(routes[position].id)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.clientsForRoute.collect { clients ->
                val spinnerClient = dialog?.findViewById<AutoCompleteTextView>(R.id.spinnerClient)
                    ?: return@collect
                val clientAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    clients.map { it.name }
                )
                spinnerClient.setAdapter(clientAdapter)
                spinnerClient.setOnItemClickListener { _, _, position, _ ->
                    if (position < clients.size) {
                        selectedClient = clients[position]
                    }
                }
            }
        }
    }

    private fun setupViews(view: View) {
        val layoutAmount   = view.findViewById<TextInputLayout>(R.id.layoutAmount)
        val inputAmount    = view.findViewById<TextInputEditText>(R.id.inputAmount)
        val layoutDueDate  = view.findViewById<TextInputLayout>(R.id.layoutDueDate)
        val inputDueDate   = view.findViewById<TextInputEditText>(R.id.inputDueDate)
        val btnTakePhoto   = view.findViewById<MaterialButton>(R.id.btnTakePhoto)
        val inputNotes     = view.findViewById<TextInputEditText>(R.id.inputNotes)
        val btnCancel      = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave        = view.findViewById<MaterialButton>(R.id.btnSave)

        // Date picker
        inputDueDate.setOnClickListener { showDatePicker(inputDueDate) }
        layoutDueDate.setEndIconOnClickListener { showDatePicker(inputDueDate) }

        // Photo
        btnTakePhoto.setOnClickListener { showPhotoOptions() }

        // Cancel
        btnCancel.setOnClickListener { dismiss() }

        // Save
        btnSave.setOnClickListener {
            val route = selectedRoute
            val client = selectedClient
            val amountStr = inputAmount.text?.toString()?.trim()
            val amount = amountStr?.toDoubleOrNull()
            val dueDate = selectedDueDateMillis

            // Validations
            when {
                route == null -> {
                    Toast.makeText(requireContext(), R.string.pending_error_select_route, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                client == null -> {
                    Toast.makeText(requireContext(), R.string.pending_error_select_client, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                amount == null || amount <= 0 -> {
                    layoutAmount.error = getString(R.string.pending_error_amount)
                    return@setOnClickListener
                }
                dueDate == null -> {
                    Toast.makeText(requireContext(), R.string.pending_error_date, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            layoutAmount.error = null

            // Pasar el path local ya copiado (no la URI original)
            viewModel.createPendingAccount(
                routeId = route!!.id,
                routeName = route.name,
                clientId = client!!.id,
                clientName = client.name,
                amountQ = amount!!,
                invoiceLocalPath = copiedLocalFilePath,
                dueDateMillis = dueDate!!,
                notes = inputNotes.text?.toString(),
            )
            dismiss()
        }
    }

    private fun showDatePicker(input: TextInputEditText) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pending_hint_due_date))
            .setSelection(selectedDueDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds())
            .setTheme(R.style.ThemeOverlay_Distribuidora_MaterialCalendar)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            selectedDueDateMillis = millis
            input.setText(dateFormat.format(Date(millis)))
        }

        picker.show(childFragmentManager, "due_date_picker")
    }

    private fun showPhotoOptions() {
        val options = arrayOf("Tomar foto", "Elegir de galería")
        MaterialAlertDialogBuilder(requireContext())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCameraIntent()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraIntent() {
        try {
            val photoFile = File.createTempFile(
                "invoice_",
                ".jpg",
                requireContext().cacheDir
            )
            capturedPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(capturedPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al abrir cámara: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "AddPendingAccountDialog"
    }
}

