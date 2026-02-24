package com.are.distribuidora.pedido.presentation.list

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.edit.EditPedidoFragment
import com.are.distribuidora.pedido.presentation.print.BluetoothPrinterDialog
import com.are.distribuidora.pedido.presentation.print.PdfTicketHelper
import com.are.distribuidora.pedido.presentation.print.PrintTicketHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RC_BLUETOOTH = 1001

/**
 * Pantalla 2 — Pedidos de una ruta específica (tarjetas por cliente).
 * Al pulsar una tarjeta navega a PedidoDetalleFragment.
 * Los 3 puntos de cada tarjeta permiten eliminar el pedido.
 * El botón de impresora lanza la impresión ESC/POS vía Bluetooth.
 */
@AndroidEntryPoint
class PedidosPorRutaFragment : Fragment(R.layout.fragment_pedidos_por_ruta) {

    private val viewModel: PedidosViewModel by activityViewModels()

    private val routeId   by lazy { requireArguments().getString(ARG_ROUTE_ID, "") }
    private val routeName by lazy { requireArguments().getString(ARG_ROUTE_NAME, "") }

    /** Pedido pendiente de imprimir mientras se solicita permiso Bluetooth. */
    private var pendingPrintPedidoId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar   = view.findViewById<MaterialToolbar>(R.id.toolbarPorRuta)
        val recycler  = view.findViewById<RecyclerView>(R.id.recyclerPorRuta)
        val textEmpty = view.findViewById<TextView>(R.id.textPorRutaEmpty)
        val progress  = view.findViewById<ProgressBar>(R.id.progressPorRuta)

        toolbar.title = routeName
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = PedidosClienteAdapter { pedido ->
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, PedidoDetalleFragment.newInstance(
                    pedidoId      = pedido.pedidoId,
                    clienteNombre = pedido.clienteNombre,
                ))
                .addToBackStack("PEDIDO_DETALLE")
                .commit()
        }

        // Conectar callback de eliminación → mostrar diálogo de confirmación
        adapter.onDeleteClick = { pedido ->
            showDeleteDialog(pedido)
        }

        // Conectar callback de edición → navegar a EditPedidoFragment (solo pedidos propios)
        adapter.onEditClick = { pedido ->
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    EditPedidoFragment.newInstance(pedidoId = pedido.pedidoId),
                )
                .addToBackStack("EDIT_PEDIDO")
                .commit()
        }

        // Conectar callback de impresión → solicitar permiso Bluetooth y enviar pedido a imprimir
        adapter.onPrintClick = { pedido ->
            launchPrint(pedido.pedidoId)
        }

        // Conectar callback de compartir PDF (disparado por swipe izquierdo en el ítem)
        adapter.onSharePdfClick = { pedido ->
            launchSharePdf(pedido.pedidoId)
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // ── Swipe-to-share (deslizar izquierda → generar y compartir PDF) ────
        val swipeHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            private val bgColor = "#E53935".toColorInt()
            private val bgPaint = Paint().apply { color = bgColor }
            private val textPaint = Paint().apply {
                color     = Color.WHITE
                textSize  = 36f
                isAntiAlias = true
                isFakeBoldText = true
            }
            private val iconDrawable by lazy {
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_pdf)
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return
                val pedido = adapter.currentList.getOrNull(position) ?: return

                // Restaurar la tarjeta inmediatamente (no queremos que desaparezca)
                adapter.notifyItemChanged(position)

                // Lanzar la generación y compartición del PDF
                launchSharePdf(pedido.pedidoId)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top

                // Solo dibujamos el fondo cuando el swipe es hacia la izquierda
                if (dX < 0) {
                    // Fondo rojo
                    val bgRect = RectF(
                        itemView.right + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                    )
                    c.drawRect(bgRect, bgPaint)

                    // Ícono PDF centrado verticalmente en el área revelada
                    val iconSize    = (itemHeight * 0.4f).toInt().coerceIn(32, 64)
                    val iconMargin  = (itemHeight - iconSize) / 2
                    val iconLeft    = itemView.right - iconMargin - iconSize
                    val iconTop     = itemView.top  + (itemHeight - iconSize) / 2
                    iconDrawable?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    iconDrawable?.draw(c)

                    // Texto "PDF" debajo del ícono (solo si hay espacio suficiente)
                    val swipeWidth = -dX
                    if (swipeWidth > iconSize + iconMargin * 2 + 20) {
                        val label = "PDF"
                        val textX = itemView.right - iconMargin - iconSize / 2f -
                                    textPaint.measureText(label) / 2f
                        val textY = iconTop + iconSize + textPaint.textSize + 2f
                        c.drawText(label, textX, textY, textPaint)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        swipeHelper.attachToRecyclerView(recycler)

        // Observar el estado reactivamente para evitar race condition con la carga asíncrona
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
                            val items = viewModel.getPedidosByRoute(routeId)
                            if (items.isEmpty()) {
                                recycler.visibility  = View.GONE
                                textEmpty.visibility = View.VISIBLE
                            } else {
                                recycler.visibility  = View.VISIBLE
                                textEmpty.visibility = View.GONE
                                adapter.submitList(items)
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

        // Observar eventos de eliminación para mostrar Snackbar
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteEvent.collect { event ->
                    when (event) {
                        is PedidosViewModel.DeleteEvent.Success ->
                            Snackbar.make(view, R.string.pedido_delete_success, Snackbar.LENGTH_SHORT).show()
                        is PedidosViewModel.DeleteEvent.Error ->
                            Snackbar.make(view, R.string.pedido_delete_error, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── PDF / Compartir ───────────────────────────────────────────────────────

    private fun launchSharePdf(pedidoId: String) {
        val pw = viewModel.getPedidoForPrint(pedidoId) ?: run {
            Snackbar.make(requireView(), "Pedido no encontrado", Snackbar.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val vendorEmail = viewModel.getVendorEmail()
            try {
                val shareIntent = withContext(Dispatchers.IO) {
                    PdfTicketHelper.createShareIntent(
                        context     = requireContext().applicationContext,
                        pw          = pw,
                        vendorEmail = vendorEmail,
                        routeId     = routeId,
                    )
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Compartir factura"))
            } catch (e: Exception) {
                Snackbar.make(
                    requireView(),
                    "Error al generar PDF: ${e.message}",
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    // ── Impresión ─────────────────────────────────────────────────────────────

    private fun launchPrint(pedidoId: String) {
        if (!PrintTicketHelper.hasBluetoothPermission(requireContext())) {
            pendingPrintPedidoId = pedidoId
            PrintTicketHelper.requestBluetoothPermissions(requireActivity(), RC_BLUETOOTH)
            return
        }
        startPrintFlow(pedidoId)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_BLUETOOTH) {
            val pedidoId = pendingPrintPedidoId ?: return
            pendingPrintPedidoId = null
            if (PrintTicketHelper.hasBluetoothPermission(requireContext())) {
                startPrintFlow(pedidoId)
            } else {
                Snackbar.make(requireView(), "Permiso Bluetooth denegado", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPrintFlow(pedidoId: String) {
        val devices = PrintTicketHelper.getPairedPrinters(requireContext())
        if (devices.isEmpty()) {
            Snackbar.make(
                requireView(),
                "No hay impresoras Bluetooth emparejadas",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        BluetoothPrinterDialog.show(childFragmentManager, devices) { device ->
            val pw = viewModel.getPedidoForPrint(pedidoId) ?: run {
                Snackbar.make(requireView(), "Pedido no encontrado", Snackbar.LENGTH_SHORT).show()
                return@show
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val vendorEmail = viewModel.getVendorEmail()
                try {
                    PrintTicketHelper.print(
                        context     = requireContext(),
                        device      = device,
                        pw          = pw,
                        vendorEmail = vendorEmail,
                        routeId     = routeId,
                    )
                    Snackbar.make(requireView(), "Ticket impreso correctamente", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(
                        requireView(),
                        "Error al imprimir: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Eliminación ───────────────────────────────────────────────────────────

    private fun showDeleteDialog(pedido: PedidoClienteUiModel) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pedido_delete_dialog_title)
            .setMessage(getString(R.string.pedido_delete_dialog_message, pedido.clienteNombre))
            .setPositiveButton(R.string.pedido_delete_confirm) { _, _ ->
                viewModel.deletePedido(pedido.pedidoId)
            }
            .setNegativeButton(R.string.pedido_delete_cancel, null)
            .show()
    }

    companion object {
        private const val ARG_ROUTE_ID   = "routeId"
        private const val ARG_ROUTE_NAME = "routeName"

        fun newInstance(routeId: String, routeName: String) = PedidosPorRutaFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROUTE_ID, routeId)
                putString(ARG_ROUTE_NAME, routeName)
            }
        }
    }
}

