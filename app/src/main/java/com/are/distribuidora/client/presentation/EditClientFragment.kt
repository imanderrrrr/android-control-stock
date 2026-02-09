package com.are.distribuidora.client.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.presentation.util.CapitalizeTextWatcher
import com.are.distribuidora.client.presentation.util.PhoneNumberInputFilter
import com.are.distribuidora.client.presentation.util.PhoneNumberTextWatcher
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditClientFragment : Fragment() {

    private val viewModel: EditClientViewModel by viewModels()
    private var clientId: String = ""

    // UI Referencias (Reutilizamos layout de CreateClient si es idéntico, 
    // pero idealmente deberíamos tener uno propio o manipular el toolbar)
    // Asumiremos que el layout `fragment_create_client` tiene los IDs necesarios.
    // Si se requiere layout distinto, se crearía `fragment_edit_client`.
    // Por simplicidad y consistencia visual, reutilizamos y ajustamos textos.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientId = requireArguments().getString(ARG_CLIENT_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_create_client, container, false)
    }

    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewModel.takeLocation()
            }
            else -> {
                Toast.makeText(context, "Permiso de ubicación necesario para esta función", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (clientId.isBlank()) {
            Toast.makeText(context, "ID de cliente inválido", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        viewModel.load(clientId)

        // Bind Views
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val nameInput = view.findViewById<EditText>(R.id.inputName)
        val phoneInput = view.findViewById<EditText>(R.id.inputPhone)
        val addressInput = view.findViewById<EditText>(R.id.inputAddress)
        val maxInput = view.findViewById<EditText>(R.id.inputMaxAmount)
        val activeSwitch = view.findViewById<SwitchMaterial>(R.id.switchActive)
        val saveButton = view.findViewById<Button>(R.id.buttonSave)
        
        // Location Views
        val locationButton = view.findViewById<Button>(R.id.buttonLocation)
        val locationProgress = view.findViewById<View>(R.id.progressLocation)
        val locationStatus = view.findViewById<android.widget.TextView>(R.id.textLocationStatus)
        val clearLocationButton = view.findViewById<View>(R.id.buttonClearLocation)

        toolbar.title = "Editar Cliente"
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        saveButton.text = "Guardar Cambios"

        // Setup formatters
        nameInput.addTextChangedListener(CapitalizeTextWatcher())
        addressInput.addTextChangedListener(CapitalizeTextWatcher())
        phoneInput.addTextChangedListener(PhoneNumberTextWatcher())
        phoneInput.filters = arrayOf(PhoneNumberInputFilter())

        // Listeners for Location
        locationButton.setOnClickListener {
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        clearLocationButton.setOnClickListener {
            viewModel.clearLocation()
        }

        // Save Click
        saveButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty()
            val phone = phoneInput.text?.toString()?.takeIf { it.isNotBlank() }
            val address = addressInput.text?.toString()?.takeIf { it.isNotBlank() }
            val max = maxInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toLongOrNull()
            
            // Ajuste: Leeremos el routeId del Success state si está disponible.
            val currentState = viewModel.uiState.value
            val currentRouteId = if (currentState is EditClientViewModel.EditClientUiState.Success) {
                currentState.client.routeId
            } else {
                "" // Should not happen if loaded
            }

            viewModel.save(
                name = name,
                phone = phone,
                address = address,
                maxOrderAmountInCents = max,
                isActive = activeSwitch.isChecked,
                routeId = currentRouteId
            )
        }

        // Observe State
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when(state) {
                            is EditClientViewModel.EditClientUiState.Loading -> {
                                saveButton.isEnabled = false
                                // Show loader if available
                            }
                            is EditClientViewModel.EditClientUiState.Error -> {
                                saveButton.isEnabled = true
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                            is EditClientViewModel.EditClientUiState.Success -> {
                                saveButton.isEnabled = true
                                populateFields(state.client, nameInput, phoneInput, addressInput, maxInput, activeSwitch)
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
                    viewModel.locationState.collect { state ->
                        // Update UI based on location state
                        locationProgress.visibility = if (state.isCapturing) View.VISIBLE else View.GONE
                        locationButton.isEnabled = !state.isCapturing

                        if (state.latitude != null && state.longitude != null) {
                            locationStatus.text = "Lat: ${state.latitude}, Lng: ${state.longitude}"
                            clearLocationButton.visibility = View.VISIBLE
                        } else {
                            locationStatus.text = "Ubicación: No capturada"
                            clearLocationButton.visibility = View.GONE
                        }

                        if (state.error != null) {
                            Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when(event) {
                            is EditClientViewModel.Event.Success -> {
                                Toast.makeText(context, "Cliente actualizado", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            }
                            is EditClientViewModel.Event.Error -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateFields(
        client: Client,
        nameInput: EditText,
        phoneInput: EditText,
        addressInput: EditText,
        maxInput: EditText,
        activeSwitch: SwitchMaterial
    ) {
        // Only populate if fields are empty (first load) to avoid overwriting user edits on recomposition/state updates
        // However, this is StateFlow, so Success emits constantly. 
        // We need a flag or check if contents match to avoid cursor jumps.
        
        if (nameInput.text.toString() != client.name) nameInput.setText(client.name)
        
        val phone = client.phone.orEmpty()
        if (phoneInput.text.toString() != phone) phoneInput.setText(phone)
        
        val address = client.address.orEmpty()
        if (addressInput.text.toString() != address) addressInput.setText(address)
        
        val max = client.maxOrderAmountInCents?.toString().orEmpty()
        if (maxInput.text.toString() != max) maxInput.setText(max)
        
        if (activeSwitch.isChecked != client.isActive) activeSwitch.isChecked = client.isActive
    }

    companion object {
        private const val ARG_CLIENT_ID = "clientId"

        fun newInstance(clientId: String): EditClientFragment {
            return EditClientFragment().apply {
                arguments = bundleOf(ARG_CLIENT_ID to clientId)
            }
        }
    }
}
