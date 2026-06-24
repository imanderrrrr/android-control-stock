package com.are.distribuidora.pedido.presentation.confirmed

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.list.PedidoDetalleFragment
import com.are.distribuidora.pedido.presentation.list.PedidosViewModel
import com.are.distribuidora.pedido.presentation.print.BluetoothPrinterDialog
import com.are.distribuidora.pedido.presentation.print.PdfTicketHelper
import com.are.distribuidora.pedido.presentation.print.PrintTicketHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val RC_BLUETOOTH_CONFIRM = 3001

/**
 * Pantalla de confirmación tras crear un pedido (réplica Pencil "09"). Solo lectura:
 * carga el pedido por id, y ofrece continuar la ruta, compartir PDF, imprimir o ver el detalle.
 */
@AndroidEntryPoint
class OrderConfirmedFragment : Fragment(R.layout.fragment_order_confirmed) {

    private val viewModel: OrderConfirmedViewModel by viewModels()
    private val pedidosViewModel: PedidosViewModel by activityViewModels()

    private val pedidoId by lazy { requireArguments().getString(ARG_PEDIDO_ID, "") }
    private val clienteNombre by lazy { requireArguments().getString(ARG_CLIENTE_NOMBRE, "") }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val textRef      = view.findViewById<TextView>(R.id.textConfirmedRef)
        val textName     = view.findViewById<TextView>(R.id.textConfirmedClientName)
        val textAddress  = view.findViewById<TextView>(R.id.textConfirmedClientAddress)
        val textProducts = view.findViewById<TextView>(R.id.textConfirmedProducts)
        val textTotal    = view.findViewById<TextView>(R.id.textConfirmedTotal)

        textName.text = clienteNombre
        viewModel.load(pedidoId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiModel.collect { ui ->
                    ui ?: return@collect
                    textRef.text      = ui.orderRef
                    textName.text     = ui.clientName
                    textAddress.text  = ui.clientAddress ?: ""
                    textAddress.visibility =
                        if (ui.clientAddress.isNullOrBlank()) View.GONE else View.VISIBLE
                    textProducts.text =
                        getString(R.string.order_confirmed_summary, ui.itemsCount, ui.unitsCount)
                    textTotal.text    = ui.totalFormatted
                }
            }
        }

        view.findViewById<View>(R.id.btnContinueRoute).setOnClickListener { finishFlow() }
        view.findViewById<View>(R.id.btnShare).setOnClickListener { launchSharePdf() }
        view.findViewById<View>(R.id.btnPrint).setOnClickListener { launchPrint() }

        view.findViewById<View>(R.id.btnViewDetail).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                .replace(
                    R.id.fragmentContainer,
                    PedidoDetalleFragment.newInstance(pedidoId = pedidoId, clienteNombre = clienteNombre),
                    "PEDIDO_DETALLE",
                )
                .addToBackStack("PEDIDO_DETALLE")
                .commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finishFlow()
            },
        )
    }

    /** Cierra todo el flujo de creación, volviendo a la pantalla anterior (Inicio/Pedidos). */
    private fun finishFlow() {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    // ── Compartir PDF ───────────────────────────────────────────────────────────

    private fun launchSharePdf() {
        val dialogView  = layoutInflater.inflate(R.layout.dialog_pdf_progress, null)
        val progressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.progressIndicatorPdf)
        val textPercent = dialogView.findViewById<TextView>(R.id.textPdfProgressPercent)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView).setCancelable(false).create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val pw = pedidosViewModel.getPedidoForPrint(pedidoId)
            if (pw == null) {
                if (isAdded) { progressDialog.dismiss(); Snackbar.make(requireView(), "Pedido no encontrado", Snackbar.LENGTH_SHORT).show() }
                return@launch
            }
            val vendorEmail = pedidosViewModel.getVendorEmail()
            val routeName   = pedidosViewModel.resolveRouteNameForPrint(pw.pedido.routeId)
            try {
                val shareIntent = PdfTicketHelper.createShareIntent(
                    context = requireContext().applicationContext, pw = pw,
                    vendorEmail = vendorEmail, routeName = routeName,
                    onProgress = { percent ->
                        if (isAdded && progressDialog.isShowing) { progressBar.progress = percent; textPercent.text = "$percent%" }
                    },
                )
                if (isAdded) { progressDialog.dismiss(); startActivity(android.content.Intent.createChooser(shareIntent, "Compartir factura")) }
            } catch (e: Exception) {
                if (isAdded) { progressDialog.dismiss(); Snackbar.make(requireView(), "Error al generar PDF: ${e.message}", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    // ── Imprimir (Bluetooth) ─────────────────────────────────────────────────────

    private fun launchPrint() {
        if (!PrintTicketHelper.hasBluetoothPermission(requireContext())) {
            PrintTicketHelper.requestBluetoothPermissions(requireActivity(), RC_BLUETOOTH_CONFIRM)
            return
        }
        startPrintFlow()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_BLUETOOTH_CONFIRM) {
            if (PrintTicketHelper.hasBluetoothPermission(requireContext())) startPrintFlow()
            else Snackbar.make(requireView(), "Permiso Bluetooth denegado", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun startPrintFlow() {
        val devices = PrintTicketHelper.getPairedPrinters(requireContext())
        if (devices.isEmpty()) {
            Snackbar.make(requireView(), "No hay impresoras Bluetooth emparejadas", Snackbar.LENGTH_LONG).show()
            return
        }
        BluetoothPrinterDialog.show(childFragmentManager, devices) { device ->
            viewLifecycleOwner.lifecycleScope.launch {
                val pw = pedidosViewModel.getPedidoForPrint(pedidoId) ?: run {
                    Snackbar.make(requireView(), "Pedido no encontrado", Snackbar.LENGTH_SHORT).show(); return@launch
                }
                val vendorEmail = pedidosViewModel.getVendorEmail()
                val routeName   = pedidosViewModel.resolveRouteNameForPrint(pw.pedido.routeId)
                try {
                    PrintTicketHelper.print(context = requireContext(), device = device, pw = pw, vendorEmail = vendorEmail, routeName = routeName)
                    Snackbar.make(requireView(), "Ticket impreso correctamente", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Snackbar.make(requireView(), "Error al imprimir: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ARG_PEDIDO_ID = "pedidoId"
        private const val ARG_CLIENTE_NOMBRE = "clienteNombre"

        fun newInstance(pedidoId: String, clienteNombre: String) = OrderConfirmedFragment().apply {
            arguments = bundleOf(
                ARG_PEDIDO_ID to pedidoId,
                ARG_CLIENTE_NOMBRE to clienteNombre,
            )
        }
    }
}
