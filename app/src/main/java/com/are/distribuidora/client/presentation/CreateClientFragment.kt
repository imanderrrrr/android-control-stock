package com.are.distribuidora.client.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.util.CapitalizeTextWatcher
import com.are.distribuidora.client.presentation.util.PhoneNumberInputFilter
import com.are.distribuidora.client.presentation.util.PhoneNumberTextWatcher
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateClientFragment : Fragment() {

    private val viewModel: CreateClientViewModel by viewModels()

    private var routeId: String = ""
    private var routeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeId = requireArguments().getString(ARG_ROUTE_ID).orEmpty()
        routeName = requireArguments().getString(ARG_ROUTE_NAME).orEmpty()
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

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = routeName
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val nameInput = view.findViewById<EditText>(R.id.inputName)
        val phoneInput = view.findViewById<EditText>(R.id.inputPhone)
        val addressInput = view.findViewById<EditText>(R.id.inputAddress)
        val maxInput = view.findViewById<EditText>(R.id.inputMaxAmount)
        val saveButton = view.findViewById<Button>(R.id.buttonSave)
        
        // Location Views
        val locationButton = view.findViewById<Button>(R.id.buttonLocation)
        val locationProgress = view.findViewById<View>(R.id.progressLocation)
        val locationStatus = view.findViewById<android.widget.TextView>(R.id.textLocationStatus)
        val clearLocationButton = view.findViewById<View>(R.id.buttonClearLocation)

        // Aplicar formateo automático de capitalización en nombre
        nameInput.addTextChangedListener(CapitalizeTextWatcher())

        // Aplicar formateo automático de capitalización en dirección
        addressInput.addTextChangedListener(CapitalizeTextWatcher())

        // Aplicar formateo automático de teléfono (XXXX XXXX)
        phoneInput.addTextChangedListener(PhoneNumberTextWatcher())
        phoneInput.filters = arrayOf(PhoneNumberInputFilter())

        locationButton.setOnClickListener {
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }

        clearLocationButton.setOnClickListener {
            viewModel.clearLocation()
        }

        saveButton.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty()
            val phone = phoneInput.text?.toString()?.takeIf { it.isNotBlank() }
            val address = addressInput.text?.toString()?.takeIf { it.isNotBlank() }
            val max = maxInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.toLongOrNull()

            viewModel.save(
                routeId = routeId,
                name = name,
                phone = phone,
                address = address,
                maxOrderAmountInCents = max
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        when (event) {
                            is CreateClientViewModel.Event.Success -> {
                                parentFragmentManager.popBackStack()
                            }

                            is CreateClientViewModel.Event.Error -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_ROUTE_ID = "routeId"
        private const val ARG_ROUTE_NAME = "routeName"

        fun newInstance(routeId: String, routeName: String): CreateClientFragment {
            return CreateClientFragment().apply {
                arguments = bundleOf(
                    ARG_ROUTE_ID to routeId,
                    ARG_ROUTE_NAME to routeName
                )
            }
        }
    }
}
