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
import com.are.distribuidora.pedido.presentation.detail.OtrosPedidoDetalleFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tab 1 — Lista de rutas con pedidos de otros vendedores.
 *
 * Incluye un filtro de fecha PROPIO (DatePicker + botón Hoy), independiente
 * del filtro de "Mis Pedidos". Por defecto muestra siempre los pedidos del día actual.
 *
 * Comportamiento de tap en tarjeta:
 *   - Si el pedido está COMPLETED → navega directo al detalle.
 *   - Si NO está descargado → inicia descarga y espera el evento Success para navegar.
 *     La navegación es NO optimista: solo ocurre al confirmar persistencia en Room.
 */
@AndroidEntryPoint
class OtrosPedidosListFragment : Fragment(R.layout.fragment_otros_pedidos_list) {

    private val viewModel: PedidosViewModel by activityViewModels()

    /**
     * Pedido que el usuario quiso abrir y que disparó una descarga.
     * Pair(orderId, clientName). Se limpia al navegar o al recibir Error de ese pedido.
     */
    private var pendingOpen: Pair<String, String>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler  = view.findViewById<RecyclerView>(R.id.recyclerOtrosPedidosList)
        val progress  = view.findViewById<ProgressBar>(R.id.progressOtrosPedidosList)
        val textEmpty = view.findViewById<TextView>(R.id.textOtrosPedidosListEmpty)
        val chipFecha = view.findViewById<Chip>(R.id.chipFechaFiltroOtros)
        val btnHoy    = view.findViewById<MaterialButton>(R.id.btnFechaHoyOtros)

        val adapter = OtrosOrdersAdapter(
            onDownloadClick = { header ->
                viewModel.downloadOrderItems(header.orderId)
            },
            onOpenClick = { header ->
                handleOpenTap(header, view)
            },
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // ── Chip: abre DatePicker al pulsar ──────────────────────────────────
        chipFecha.setOnClickListener { showDatePicker() }

        // ── Botón "Hoy": resetea el filtro al día actual ─────────────────────
        btnHoy.setOnClickListener {
            viewModel.setOtrosSelectedDate(viewModel.todayAsDbString())
        }

        // ── Observar la fecha de Otros Pedidos para actualizar el chip ────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.otrosSelectedDate.collect { dbDate ->
                    chipFecha.text = viewModel.formatDateForDisplay(dbDate)
                    btnHoy.visibility =
                        if (dbDate == viewModel.todayAsDbString()) View.GONE else View.VISIBLE
                }
            }
        }

        // ── Propagar downloadingIds al adapter para mostrar spinner por tarjeta
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadingIds.collect { ids ->
                    adapter.downloadingIds = ids
                }
            }
        }

        // ── Observar el estado reactivo de Otros Pedidos ─────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.otrosUiState.collect { state ->
                    when (state) {
                        is PedidosViewModel.OtrosUiState.Loading -> {
                            progress.visibility  = View.VISIBLE
                            recycler.visibility  = View.GONE
                            textEmpty.visibility = View.GONE
                        }
                        is PedidosViewModel.OtrosUiState.Success -> {
                            progress.visibility = View.GONE
                            if (state.routes.isEmpty()) {
                                recycler.visibility  = View.GONE
                                textEmpty.visibility = View.VISIBLE
                            } else {
                                recycler.visibility  = View.VISIBLE
                                textEmpty.visibility = View.GONE
                                adapter.submitList(state.routes)
                            }
                        }
                        is PedidosViewModel.OtrosUiState.Error -> {
                            progress.visibility  = View.GONE
                            recycler.visibility  = View.GONE
                            textEmpty.visibility = View.VISIBLE
                            textEmpty.text       = state.message
                        }
                    }
                }
            }
        }

        // ── Observar eventos de descarga para navegar o mostrar feedback ──────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadEvent.collect { event ->
                    when (event) {
                        is PedidosViewModel.DownloadEvent.Success -> {
                            Snackbar.make(view, R.string.otros_download_success, Snackbar.LENGTH_SHORT).show()
                            val pending = pendingOpen
                            if (pending != null && pending.first == event.orderId) {
                                pendingOpen = null
                                navigateToDetalle(orderId = event.orderId, clientName = pending.second)
                            }
                        }
                        is PedidosViewModel.DownloadEvent.Error -> {
                            if (pendingOpen?.first == event.orderId) {
                                pendingOpen = null
                            }
                            Snackbar.make(view, R.string.otros_download_error, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // Iniciar observación reactiva de Room para TODAS las rutas conocidas,
        // filtrada por la fecha propia de Otros Pedidos.
        viewModel.startObservingAllOtherOrders()
    }

    // ── DatePicker ────────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentEpoch = try {
            dbFormat.parse(viewModel.otrosSelectedDate.value)?.time
                ?: MaterialDatePicker.todayInUtcMilliseconds()
        } catch (_: Exception) {
            MaterialDatePicker.todayInUtcMilliseconds()
        }

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pedidos_date_filter_title))
            .setSelection(currentEpoch)
            .setTheme(R.style.ThemeOverlay_Distribuidora_MaterialCalendar)
            .build()

        picker.addOnPositiveButtonClickListener { selectionEpoch ->
            val selectedDate = dbFormat.format(Date(selectionEpoch))
            viewModel.setOtrosSelectedDate(selectedDate)
        }

        picker.show(parentFragmentManager, "DATE_PICKER_OTROS_PEDIDOS")
    }

    // ── Lógica de apertura de tarjeta ─────────────────────────────────────────

    /**
     * - COMPLETED → navegar directo.
     * - Descargando (IN_PROGRESS) → informar al usuario que espere.
     * - PENDING / FAILED → iniciar descarga y registrar como pendiente de apertura.
     */
    private fun handleOpenTap(header: OtrosOrderHeaderUiModel, view: View) {
        when (header.downloadStatus) {
            OtrosDownloadUiStatus.COMPLETED -> {
                navigateToDetalle(orderId = header.orderId, clientName = header.clientName)
            }
            OtrosDownloadUiStatus.IN_PROGRESS -> {
                if (pendingOpen == null) {
                    pendingOpen = Pair(header.orderId, header.clientName)
                }
                Snackbar.make(view, R.string.otros_download_in_progress, Snackbar.LENGTH_SHORT).show()
            }
            OtrosDownloadUiStatus.PENDING,
            OtrosDownloadUiStatus.FAILED -> {
                pendingOpen = Pair(header.orderId, header.clientName)
                viewModel.downloadOrderItems(header.orderId)
            }
        }
    }

    private fun navigateToDetalle(orderId: String, clientName: String) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                OtrosPedidoDetalleFragment.newInstance(
                    orderId    = orderId,
                    clientName = clientName,
                )
            )
            .addToBackStack("OTROS_PEDIDO_DETALLE")
            .commit()
    }
}

