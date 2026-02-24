package com.are.distribuidora.pedido.presentation.list

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tab 0 — Lista de rutas con pedidos propios.
 * Incluye un filtro de fecha (DatePicker) que permite al usuario ver los pedidos
 * de cualquier día. Por defecto muestra siempre los pedidos del día actual.
 */
@AndroidEntryPoint
class MisPedidosListFragment : Fragment(R.layout.fragment_mis_pedidos_list) {

    private val viewModel: PedidosViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler    = view.findViewById<RecyclerView>(R.id.recyclerMisPedidos)
        val progress    = view.findViewById<ProgressBar>(R.id.progressMisPedidos)
        val textEmpty   = view.findViewById<TextView>(R.id.textMisPedidosEmpty)
        val chipFecha   = view.findViewById<Chip>(R.id.chipFechaFiltro)
        val btnHoy      = view.findViewById<MaterialButton>(R.id.btnFechaHoy)

        val adapter = PedidosRouteAdapter { route ->
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    PedidosPorRutaFragment.newInstance(
                        routeId   = route.routeId,
                        routeName = route.routeName,
                    )
                )
                .addToBackStack("PEDIDOS_POR_RUTA")
                .commit()
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // ── Chip: muestra la fecha activa y abre el DatePicker al pulsar ──────
        chipFecha.setOnClickListener { showDatePicker() }

        // ── Botón "Hoy": resetea el filtro al día actual ───────────────────────
        btnHoy.setOnClickListener {
            viewModel.setSelectedDate(viewModel.todayAsDbString())
        }

        // ── Observar la fecha seleccionada para actualizar el chip ─────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedDate.collect { dbDate ->
                    chipFecha.text = viewModel.formatDateForDisplay(dbDate)
                    // Ocultar "Hoy" si ya estamos en hoy
                    btnHoy.visibility =
                        if (dbDate == viewModel.todayAsDbString()) View.GONE else View.VISIBLE
                }
            }
        }

        // ── Observar el estado de la lista de rutas ────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PedidosViewModel.UiState.Loading -> {
                            progress.visibility  = View.VISIBLE
                            recycler.visibility  = View.GONE
                            textEmpty.visibility = View.GONE
                        }
                        is PedidosViewModel.UiState.Success -> {
                            progress.visibility = View.GONE
                            if (state.routes.isEmpty()) {
                                recycler.visibility  = View.GONE
                                textEmpty.visibility = View.VISIBLE
                                textEmpty.text       = getString(R.string.pedidos_empty_date)
                            } else {
                                recycler.visibility  = View.VISIBLE
                                textEmpty.visibility = View.GONE
                                adapter.submitList(state.routes)
                            }
                        }
                        is PedidosViewModel.UiState.Error -> {
                            progress.visibility  = View.GONE
                            recycler.visibility  = View.GONE
                            textEmpty.visibility = View.VISIBLE
                            textEmpty.text       = state.message
                        }
                    }
                }
            }
        }
    }

    // ── DatePicker ────────────────────────────────────────────────────────────

    private fun showDatePicker() {
        // Convertir la fecha activa a epoch UTC para posicionar el picker
        val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentEpoch = try {
            dbFormat.parse(viewModel.selectedDate.value)?.time
                ?: MaterialDatePicker.todayInUtcMilliseconds()
        } catch (_: Exception) {
            MaterialDatePicker.todayInUtcMilliseconds()
        }

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pedidos_date_filter_title))
            .setSelection(currentEpoch)
            .build()

        picker.addOnPositiveButtonClickListener { selectionEpoch ->
            // MaterialDatePicker devuelve epoch UTC midnight; lo convertimos a "YYYY-MM-DD"
            val selectedDate = dbFormat.format(Date(selectionEpoch))
            viewModel.setSelectedDate(selectedDate)
        }

        picker.show(parentFragmentManager, "DATE_PICKER_PEDIDOS")
    }
}

