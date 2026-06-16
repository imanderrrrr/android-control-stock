package com.are.distribuidora.pendingaccount.presentation

import android.Manifest
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.bumptech.glide.Glide
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 10a · Nueva cuenta / Editar cuenta.
 *
 * La fecha se normaliza a medianoche LOCAL al elegirla (el MaterialDatePicker
 * entrega medianoche UTC), de modo que el ViewModel almacena un valor consistente
 * con el cálculo de "vencida". Para que el alta sea completable sin abrir el
 * selector, la fecha arranca con un valor por defecto (hoy + 7 días).
 */
@AndroidEntryPoint
class PendingAccountFormFragment : Fragment(R.layout.fragment_pending_account_form) {

    private val viewModel: PendingAccountsViewModel by viewModels()
    private val editingId: String? by lazy { arguments?.getString(ARG_ID) }
    private val isEdit get() = editingId != null

    private var selectedRouteId: String? = null
    private var selectedRouteName: String? = null
    private var selectedClientId: String? = null
    private var selectedClientName: String? = null
    private var selectedDueDateMillis: Long = 0L

    /** Foto nueva copiada en esta sesión (null si no se eligió ninguna). */
    private var copiedLocalFilePath: String? = null
    private var photoRemoved = false
    private var existingPhotoUri: String? = null
    private var existingRemoteUrl: String? = null
    private var capturedPhotoUri: Uri? = null

    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("es", "GT"))

    // ── Views ───────────────────────────────────────────────────────────────────
    private lateinit var spinnerRoute: AutoCompleteTextView
    private lateinit var spinnerClient: AutoCompleteTextView
    private lateinit var inputAmount: TextInputEditText
    private lateinit var inputDueDate: TextInputEditText
    private lateinit var inputNotes: TextInputEditText
    private lateinit var layoutAmount: TextInputLayout
    private lateinit var photoTile: View
    private lateinit var photoPreviewContainer: View
    private lateinit var imagePreview: ShapeableImageView

    // ── Photo launchers ──────────────────────────────────────────────────────────
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && capturedPhotoUri != null) onPhotoPicked(capturedPhotoUri!!)
        }
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onPhotoPicked(uri)
        }
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraIntent()
            else Toast.makeText(requireContext(), R.string.cxc_error_generic, Toast.LENGTH_SHORT).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        spinnerRoute = view.findViewById(R.id.spinnerRoute)
        spinnerClient = view.findViewById(R.id.spinnerClient)
        inputAmount = view.findViewById(R.id.inputAmount)
        inputDueDate = view.findViewById(R.id.inputDueDate)
        inputNotes = view.findViewById(R.id.inputNotes)
        layoutAmount = view.findViewById(R.id.layoutAmount)
        photoTile = view.findViewById(R.id.photoTile)
        photoPreviewContainer = view.findViewById(R.id.photoPreviewContainer)
        imagePreview = view.findViewById(R.id.imagePreview)

        val title = view.findViewById<android.widget.TextView>(R.id.textTitle)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        title.setText(if (isEdit) R.string.cxc_form_title_edit else R.string.cxc_form_title_new)
        btnSave.setText(if (isEdit) R.string.cxc_form_save_edit else R.string.cxc_form_save)

        view.findViewById<View>(R.id.btnBack).setOnClickListener { goBack() }
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { goBack() }
        view.findViewById<View>(R.id.layoutDueDate).setOnClickListener { showDatePicker() }
        inputDueDate.setOnClickListener { showDatePicker() }
        photoTile.setOnClickListener { showPhotoOptions() }
        imagePreview.setOnClickListener { showPhotoOptions() }
        view.findViewById<MaterialButton>(R.id.btnRemovePhoto).setOnClickListener { removePhoto() }
        btnSave.setOnClickListener { save() }

        // Fecha por defecto: hoy + 7 días (medianoche local).
        selectedDueDateMillis = defaultDueDateMillis()
        inputDueDate.setText(dateFmt.format(Date(selectedDueDateMillis)))

        observeRoutesAndClients()

        if (isEdit) prefillForEdit()
    }

    private fun observeRoutesAndClients() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.routes.collect { routes ->
                        spinnerRoute.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, routes.map { it.name })
                        )
                        spinnerRoute.setOnItemClickListener { _, _, pos, _ ->
                            val r = routes[pos]
                            selectedRouteId = r.id
                            selectedRouteName = r.name
                            selectedClientId = null
                            selectedClientName = null
                            spinnerClient.setText("", false)
                            viewModel.loadClientsForRoute(r.id)
                        }
                    }
                }
                launch {
                    viewModel.clientsForRoute.collect { clients ->
                        spinnerClient.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, clients.map { it.name })
                        )
                        spinnerClient.setOnItemClickListener { _, _, pos, _ ->
                            selectedClientId = clients[pos].id
                            selectedClientName = clients[pos].name
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PendingAccountsViewModel.Event.Created -> { toast(R.string.cxc_toast_created); goBack() }
                            is PendingAccountsViewModel.Event.Updated -> { toast(R.string.cxc_toast_updated); goBack() }
                            is PendingAccountsViewModel.Event.Error ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun prefillForEdit() {
        val id = editingId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val e = viewModel.getAccount(id) ?: return@launch
            selectedRouteId = e.routeId
            selectedRouteName = e.routeName
            selectedClientId = e.clientId
            selectedClientName = e.clientName
            existingPhotoUri = e.invoicePhotoUri
            existingRemoteUrl = e.invoiceRemoteUrl
            selectedDueDateMillis = e.dueDateMillis

            spinnerRoute.setText(e.routeName, false)
            spinnerClient.setText(e.clientName, false)
            viewModel.loadClientsForRoute(e.routeId)
            inputAmount.setText(String.format(Locale.US, "%.2f", e.amountCents / 100.0))
            inputDueDate.setText(dateFmt.format(Date(e.dueDateMillis)))
            e.notes?.let { inputNotes.setText(it) }

            invoiceSourceFor(e)?.let { showPreview(it) }
        }
    }

    private fun save() {
        val routeId = selectedRouteId
        val routeName = selectedRouteName
        val clientId = selectedClientId
        val clientName = selectedClientName
        val amount = inputAmount.text?.toString()?.trim()?.toDoubleOrNull()

        when {
            routeId == null || routeName == null -> { toast(R.string.cxc_form_err_route); return }
            clientId == null || clientName == null -> { toast(R.string.cxc_form_err_client); return }
            amount == null || amount <= 0 -> { layoutAmount.error = getString(R.string.cxc_form_err_amount); return }
            selectedDueDateMillis <= 0L -> { toast(R.string.cxc_form_err_date); return }
        }
        layoutAmount.error = null
        val notes = inputNotes.text?.toString()

        if (isEdit) {
            val finalPhotoUri: String?
            val finalRemoteUrl: String?
            val isNewPhoto: Boolean
            when {
                copiedLocalFilePath != null -> {
                    finalPhotoUri = copiedLocalFilePath; finalRemoteUrl = null; isNewPhoto = true
                }
                photoRemoved -> {
                    finalPhotoUri = null; finalRemoteUrl = null; isNewPhoto = false
                }
                else -> {
                    finalPhotoUri = existingPhotoUri; finalRemoteUrl = existingRemoteUrl; isNewPhoto = false
                }
            }
            viewModel.updatePendingAccount(
                id = editingId!!,
                routeId = routeId!!,
                routeName = routeName!!,
                clientId = clientId!!,
                clientName = clientName!!,
                amountQ = amount!!,
                dueDateMillis = selectedDueDateMillis,
                notes = notes,
                invoicePhotoUri = finalPhotoUri,
                invoiceRemoteUrl = finalRemoteUrl,
                isNewPhoto = isNewPhoto,
            )
        } else {
            viewModel.createPendingAccount(
                routeId = routeId!!,
                routeName = routeName!!,
                clientId = clientId!!,
                clientName = clientName!!,
                amountQ = amount!!,
                invoiceLocalPath = copiedLocalFilePath,
                dueDateMillis = selectedDueDateMillis,
                notes = notes,
            )
        }
    }

    // ── Fecha ────────────────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.cxc_form_due)
            .setSelection(toPickerUtc(selectedDueDateMillis))
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            selectedDueDateMillis = normalizeFromPicker(utcMillis)
            inputDueDate.setText(dateFmt.format(Date(selectedDueDateMillis)))
        }
        picker.show(childFragmentManager, "due_date_picker")
    }

    private fun defaultDueDateMillis(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 7)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun normalizeFromPicker(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    private fun toPickerUtc(localMidnight: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMidnight }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    // ── Foto ─────────────────────────────────────────────────────────────────────

    private fun showPhotoOptions() {
        val options = arrayOf(getString(R.string.cxc_photo_camera), getString(R.string.cxc_photo_gallery))
        MaterialAlertDialogBuilder(requireContext())
            .setItems(options) { _, which -> if (which == 0) launchCamera() else pickImageLauncher.launch("image/*") }
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraIntent() {
        try {
            val photoFile = File.createTempFile("invoice_", ".jpg", requireContext().cacheDir)
            capturedPhotoUri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", photoFile
            )
            takePictureLauncher.launch(capturedPhotoUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.cxc_error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPhotoPicked(uri: Uri) {
        copiedLocalFilePath = copyPhotoToFilesDir(uri)
        photoRemoved = false
        copiedLocalFilePath?.let { showPreview(File(it)) }
    }

    private fun removePhoto() {
        copiedLocalFilePath = null
        photoRemoved = true
        existingPhotoUri = null
        existingRemoteUrl = null
        hidePreview()
    }

    private fun showPreview(source: Any) {
        photoTile.isVisible = false
        photoPreviewContainer.isVisible = true
        Glide.with(this).load(source).centerCrop().placeholder(R.drawable.ic_receipt_long).into(imagePreview)
    }

    private fun hidePreview() {
        photoTile.isVisible = true
        photoPreviewContainer.isVisible = false
    }

    private fun invoiceSourceFor(e: PendingAccountEntity): Any? {
        val remote = e.invoiceRemoteUrl?.trim()?.takeIf { it.isNotEmpty() }
        val local = e.invoicePhotoUri?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            remote != null -> remote
            local != null -> if (local.startsWith("content://") || local.startsWith("file://")) local else File(local)
            else -> null
        }
    }

    private fun copyPhotoToFilesDir(sourceUri: Uri): String? = try {
        val dir = File(requireContext().filesDir, "invoices").apply { if (!exists()) mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        requireContext().contentResolver.openInputStream(sourceUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (dest.exists() && dest.length() > 0) dest.absolutePath else null
    } catch (e: Exception) {
        Log.e("PendingAccountForm", "Error copying photo", e)
        null
    }

    private fun goBack() = requireActivity().onBackPressedDispatcher.onBackPressed()

    private fun toast(resId: Int) = Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val ARG_ID = "accountId"
        fun newInstance(accountId: String?) = PendingAccountFormFragment().apply {
            arguments = Bundle().apply { putString(ARG_ID, accountId) }
        }
    }
}
