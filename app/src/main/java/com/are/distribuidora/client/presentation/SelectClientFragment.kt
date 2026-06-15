package com.are.distribuidora.client.presentation

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.util.CapitalizeTextWatcher
import com.are.distribuidora.client.presentation.util.PhoneNumberInputFilter
import com.are.distribuidora.client.presentation.util.PhoneNumberTextWatcher
import com.are.distribuidora.databinding.FragmentSelectClientBinding
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.route.domain.model.Route
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SelectClientFragment : Fragment(R.layout.fragment_select_client) {

    private val viewModel: SelectClientViewModel by viewModels()
    private var binding: FragmentSelectClientBinding? = null

    private var routes: List<Route> = emptyList()

    companion object {
        const val REQUEST_KEY_CLIENT_SELECTION = "REQUEST_KEY_CLIENT_SELECTION"
        const val BUNDLE_KEY_SELECTION = "BUNDLE_KEY_SELECTION"
        const val BUNDLE_KEY_ROUTE_ID = "BUNDLE_KEY_ROUTE_ID"
        private const val ARG_ROUTE_ID = "ARG_ROUTE_ID"

        fun newInstance(): SelectClientFragment = SelectClientFragment()

        fun newInstance(routeId: String): SelectClientFragment {
            return SelectClientFragment().apply {
                arguments = bundleOf(ARG_ROUTE_ID to routeId)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSelectClientBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        setupUI()
        observeState()

        // La ruta ya se eligió en la pantalla de selección de ruta (03):
        // ocultamos el dropdown de ruta para no duplicar el paso.
        arguments?.getString(ARG_ROUTE_ID)?.let { routeIdArg ->
            viewModel.ensureRouteSelected(routeIdArg)
            binding?.routeDropdownLayout?.visibility = View.GONE
        }

        renderNoRouteSelectedState()
    }

    private fun setupUI() {
        val adapter = SelectClientAdapter { clientUi ->
            returnSelection(
                ClienteSelection.Existente(clientUi.id)
            )
        }

        binding?.apply {
            btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter

            // Estado inicial: route obligatoria
            searchInput.isEnabled = false
            btnTemporal.visibility = View.GONE
            recyclerView.visibility = View.GONE

            // Dropdown: al seleccionar ruta cargar clientes + habilitar UI
            routeDropdown.setOnItemClickListener { _, _, position, _ ->
                val route = routes.getOrNull(position) ?: return@setOnItemClickListener

                // Reset UI por cambio de ruta para evitar mezcla visual
                (recyclerView.adapter as? SelectClientAdapter)?.submitList(emptyList())
                recyclerView.scrollToPosition(0)
                searchInput.setText("")

                viewModel.onRouteSelected(route.id)
                renderRouteSelectedState()
            }

            searchInput.doAfterTextChanged { text ->
                // VM se encarga de ignorar si no hay ruta, pero UX deshabilita igual.
                viewModel.onSearchQueryChanged(text?.toString().orEmpty())
            }

            btnTemporal.setOnClickListener {
                // Solo debería ser visible si hay ruta, pero igual blindamos.
                if (viewModel.selectedRouteId.value.isNullOrBlank()) return@setOnClickListener
                showTemporaryClientDialog()
            }
        }
    }

    private fun showTemporaryClientDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_temporary_client, null)

        val nameInput    = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val phoneInput   = dialogView.findViewById<TextInputEditText>(R.id.phoneInput)
        val addressInput = dialogView.findViewById<TextInputEditText>(R.id.addressInput)
        val errorText    = dialogView.findViewById<TextView>(R.id.errorText)

        // ── Formatters idénticos a CreateClientFragment ──────────────────────
        nameInput.addTextChangedListener(CapitalizeTextWatcher())
        addressInput.addTextChangedListener(CapitalizeTextWatcher())
        phoneInput.addTextChangedListener(PhoneNumberTextWatcher())
        phoneInput.filters = arrayOf(PhoneNumberInputFilter())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Seleccionar cliente temporal")
            .setView(dialogView)
            .setPositiveButton("Confirmar", null) // se setea luego para validar sin cerrar
            .setNeutralButton("Solo consumidor final") { _, _ ->
                val snapshot = ClienteSnapshot("Consumidor Final", null, null)
                returnSelection(ClienteSelection.Temporal(snapshot))
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            confirmButton.setOnClickListener {
                val nombre    = nameInput.text?.toString()?.trim().orEmpty()
                val telefono  = phoneInput.text?.toString()?.trim().orEmpty()
                val direccion = addressInput.text?.toString()?.trim().orEmpty()

                // Al menos un campo debe tener datos
                val hasAny = nombre.isNotBlank() || telefono.isNotBlank() || direccion.isNotBlank()
                if (!hasAny) {
                    errorText.visibility = View.VISIBLE
                    errorText.text = "Ingresa al menos un dato (nombre, teléfono o dirección)"
                    return@setOnClickListener
                }

                // Si se ingresó teléfono, debe tener exactamente 8 dígitos (formato: XXXX XXXX)
                if (telefono.isNotBlank()) {
                    val digitsOnly = telefono.filter { it.isDigit() }
                    if (digitsOnly.length != 8) {
                        errorText.visibility = View.VISIBLE
                        errorText.text = "El teléfono debe tener exactamente 8 dígitos (formato: XXXX XXXX)"
                        return@setOnClickListener
                    }
                }

                errorText.visibility = View.GONE

                val snapshot = ClienteSnapshot(
                    nombre    = if (nombre.isBlank()) "Cliente temporal" else nombre,
                    telefono  = telefono.takeIf { it.isNotBlank() },
                    direccion = direccion.takeIf { it.isNotBlank() }
                )
                returnSelection(ClienteSelection.Temporal(snapshot))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.routesState.collect { state ->
                        val binding = binding ?: return@collect
                        when (state) {
                            is RoutesUiState.Loading -> {
                                binding.routeDropdownLayout.isEnabled = false
                                binding.routeDropdownLayout.error = null
                            }

                            is RoutesUiState.Success -> {
                                binding.routeDropdownLayout.isEnabled = true
                                binding.routeDropdownLayout.error = null
                                routes = state.routes
                                bindRoutesDropdown(routes)
                            }

                            is RoutesUiState.Error -> {
                                binding.routeDropdownLayout.isEnabled = true
                                binding.routeDropdownLayout.error = state.message ?: "Error cargando rutas"
                                routes = emptyList()
                                bindRoutesDropdown(routes)
                            }
                        }
                    }
                }

                launch {
                    viewModel.selectedRouteId.collect { routeId ->
                        // Si por restore ya hay routeId, habilitar UI.
                        if (routeId.isNullOrBlank()) {
                            renderNoRouteSelectedState()
                        } else {
                            renderRouteSelectedState()
                        }
                    }
                }

                launch {
                    viewModel.searchQuery.collect { query ->
                        // Si VM resetea el query, reflejarlo sin loops.
                        val binding = binding ?: return@collect
                        if (binding.searchInput.text?.toString() != query) {
                            binding.searchInput.setText(query)
                            binding.searchInput.setSelection(query.length)
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        val binding = binding ?: return@collect
                        // Si no hay ruta, nunca mostramos lista (aunque algo llegara por error)
                        if (viewModel.selectedRouteId.value.isNullOrBlank()) {
                            renderNoRouteSelectedState()
                            return@collect
                        }

                        when (state) {
                            is SelectClientUiState.Idle -> { /* */ }
                            is SelectClientUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.emptyState.visibility = View.GONE
                                binding.recyclerView.visibility = View.GONE
                            }

                            is SelectClientUiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.emptyState.visibility = View.GONE
                                binding.recyclerView.visibility = View.VISIBLE
                                (binding.recyclerView.adapter as? SelectClientAdapter)?.submitList(state.clients)
                            }

                            is SelectClientUiState.Empty -> {
                                binding.progressBar.visibility = View.GONE
                                binding.emptyState.visibility = View.VISIBLE
                                binding.emptyState.text = "Sin clientes para esta ruta"
                                binding.recyclerView.visibility = View.GONE
                            }

                            is SelectClientUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.emptyState.visibility = View.VISIBLE
                                binding.emptyState.text = "Error cargando clientes"
                                binding.recyclerView.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindRoutesDropdown(routes: List<Route>) {
        val binding = binding ?: return
        val routeNames = routes.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, routeNames)
        binding.routeDropdown.setAdapter(adapter)

        // Si hay un routeId ya seleccionado (restore/args), reflejarlo en el dropdown.
        val selectedId = viewModel.selectedRouteId.value
        if (!selectedId.isNullOrBlank()) {
            val idx = routes.indexOfFirst { it.id == selectedId }
            if (idx >= 0) {
                binding.routeDropdown.setText(routes[idx].name, false)
            }
        } else {
            // No autoselección: mantener vacío
            if (binding.routeDropdown.text?.isNotEmpty() == true) {
                binding.routeDropdown.setText("", false)
            }
        }
    }

    private fun renderNoRouteSelectedState() {
        val binding = binding ?: return
        binding.searchInput.isEnabled = false
        binding.btnTemporal.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.emptyState.text = "Selecciona una ruta"
    }

    private fun renderRouteSelectedState() {
        val binding = binding ?: return
        binding.searchInput.isEnabled = true
        binding.btnTemporal.visibility = View.VISIBLE
        // la lista/loader los controla uiState
        binding.emptyState.visibility = View.GONE
    }

    private fun returnSelection(selection: ClienteSelection) {
        val routeId = viewModel.selectedRouteId.value

        // IMPORTANTE: contrato intacto (keys + payload)
        setFragmentResult(
            REQUEST_KEY_CLIENT_SELECTION,
            bundleOf(
                BUNDLE_KEY_SELECTION to selection,
                BUNDLE_KEY_ROUTE_ID to (routeId ?: "")
            )
        )
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
