package com.are.distribuidora.stockmovement.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.presentation.product.BarcodeScannerFragment
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.screenaccess.presentation.ScreenAccessViewModel
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pantalla "Nuevo vale": movimiento manual de ENTRADA o SALIDA de un producto que no viene de un
 * pedido (compra, devolución, ajuste, merma, otro). Gate (4.1.4): el SENTIDO es un permiso —
 * CREATE_INBOUND_VOUCHER (admin y vendedor) / CREATE_OUTBOUND_VOUCHER (solo admin). Aquí se oculta
 * el botón del sentido no concedido y CreateStockVoucherUseCase lo vuelve a comprobar.
 */
@AndroidEntryPoint
class NewVoucherFragment : Fragment() {

    private val viewModel: NewVoucherViewModel by viewModels()
    private val screenAccessViewModel: ScreenAccessViewModel by activityViewModels()

    private lateinit var toggleType: MaterialButtonToggleGroup
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var resultsContainer: LinearLayout
    private lateinit var selectedCard: MaterialCardView
    private lateinit var selectedName: TextView
    private lateinit var selectedMeta: TextView
    private lateinit var selectedStock: TextView
    private lateinit var quantityLayout: TextInputLayout
    private lateinit var quantityInput: TextInputEditText
    private lateinit var reasonInput: AutoCompleteTextView
    private lateinit var noteInput: TextInputEditText
    private lateinit var confirmButton: MaterialButton

    private var reasonOptions: List<MovementReason> = MovementReason.manual

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_new_voucher, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }
        toggleType = view.findViewById(R.id.toggleType)
        searchLayout = view.findViewById(R.id.searchLayout)
        searchInput = view.findViewById(R.id.searchInput)
        resultsContainer = view.findViewById(R.id.resultsContainer)
        selectedCard = view.findViewById(R.id.selectedCard)
        selectedName = view.findViewById(R.id.selectedName)
        selectedMeta = view.findViewById(R.id.selectedMeta)
        selectedStock = view.findViewById(R.id.selectedStock)
        quantityLayout = view.findViewById(R.id.quantityLayout)
        quantityInput = view.findViewById(R.id.quantityInput)
        reasonInput = view.findViewById(R.id.reasonInput)
        noteInput = view.findViewById(R.id.noteInput)
        confirmButton = view.findViewById(R.id.btnConfirm)

        toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setType(if (checkedId == R.id.btnTypeIn) MovementType.ENTRADA else MovementType.SALIDA)
        }
        searchInput.doAfterTextChanged { viewModel.onSearchQueryChanged(it?.toString().orEmpty()) }
        quantityInput.doAfterTextChanged { updateResultingStock() }
        view.findViewById<View>(R.id.btnScan).setOnClickListener { openScanner() }
        view.findViewById<View>(R.id.btnClearProduct).setOnClickListener { viewModel.clearProduct() }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, reasonOptions.map { reasonLabel(it) })
        reasonInput.setAdapter(adapter)
        reasonInput.setOnItemClickListener { _, _, position, _ -> viewModel.setReason(reasonOptions[position]) }

        confirmButton.setOnClickListener {
            viewModel.submit(quantityInput.text?.toString().orEmpty(), noteInput.text?.toString())
        }

        parentFragmentManager.setFragmentResultListener(SCAN_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            bundle.getString(BarcodeScannerFragment.DEFAULT_RESULT_KEY)?.let { viewModel.onBarcodeScanned(it) }
        }

        if (savedInstanceState == null) {
            val presetType = arguments?.getString(ARG_TYPE)?.let { runCatching { MovementType.valueOf(it) }.getOrNull() }
            viewModel.preset(presetType, arguments?.getString(ARG_PRODUCT_ID))
        }

        // 4.1.4: solo se ofrece el sentido que el rol concede; se aplica YA con el valor actual
        // (sin esperar al primer frame, para que "Salida" no parpadee ante un vendedor) y se sigue
        // observando por si el rol cambia. Ocultar un botón no es control de acceso: el caso de
        // uso responde Forbidden igualmente.
        applyVoucherDirectionAccess(view, screenAccessViewModel.access.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
                launch { screenAccessViewModel.access.collect { applyVoucherDirectionAccess(view, it) } }
            }
        }
    }

    private fun applyVoucherDirectionAccess(view: View, access: com.are.distribuidora.screenaccess.domain.model.UserAccess) {
        val canIn = access.can(Permission.CREATE_INBOUND_VOUCHER)
        val canOut = access.can(Permission.CREATE_OUTBOUND_VOUCHER)
        view.findViewById<View>(R.id.btnTypeIn).visibility = if (canIn) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.btnTypeOut).visibility = if (canOut) View.VISIBLE else View.GONE
        val type = viewModel.uiState.value.type
        if (type == MovementType.SALIDA && !canOut && canIn) viewModel.setType(MovementType.ENTRADA)
        if (type == MovementType.ENTRADA && !canIn && canOut) viewModel.setType(MovementType.SALIDA)
    }

    private fun render(state: NewVoucherUiState) {
        val wantedId = if (state.type == MovementType.ENTRADA) R.id.btnTypeIn else R.id.btnTypeOut
        if (toggleType.checkedButtonId != wantedId) toggleType.check(wantedId)

        val product = state.product
        selectedCard.visibility = if (product != null) View.VISIBLE else View.GONE
        searchLayout.visibility = if (product == null) View.VISIBLE else View.GONE
        if (product != null) {
            selectedName.text = product.name
            val meta = listOfNotNull(product.category?.takeIf { it.isNotBlank() }, product.barcode?.takeIf { it.isNotBlank() })
            selectedMeta.text = meta.joinToString(" · ")
            selectedMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
            selectedStock.text = getString(R.string.voucher_current_stock, product.stock.value)
            selectedStock.setTextColor(
                ContextCompat.getColor(requireContext(), if (product.stock.value < 0) R.color.danger_text else R.color.text_secondary)
            )
        }
        renderResults(state.searchResults)

        val reasonText = reasonLabel(state.reason)
        if (reasonInput.text?.toString() != reasonText) reasonInput.setText(reasonText, false)

        confirmButton.isEnabled = !state.isSaving
        confirmButton.text = if (state.isSaving) "…" else getString(R.string.voucher_confirm)
        updateResultingStock()
    }

    private fun updateResultingStock() {
        val state = viewModel.uiState.value
        val product = state.product ?: return
        val qty = quantityInput.text?.toString()?.trim()?.toIntOrNull()
        if (qty == null || qty <= 0) {
            selectedStock.text = getString(R.string.voucher_current_stock, product.stock.value)
            return
        }
        val resulting = product.stock.value + state.type.sign * qty
        selectedStock.text = getString(R.string.voucher_current_stock, product.stock.value) +
            "  →  " + getString(R.string.voucher_resulting_stock, resulting)
        selectedStock.setTextColor(
            ContextCompat.getColor(requireContext(), if (resulting < 0) R.color.danger_text else R.color.text_secondary)
        )
    }

    private fun renderResults(results: List<Product>) {
        resultsContainer.removeAllViews()
        resultsContainer.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        results.forEach { product ->
            val row = inflater.inflate(R.layout.item_voucher_product_result, resultsContainer, false)
            row.findViewById<TextView>(R.id.resultName).text = product.name
            row.findViewById<TextView>(R.id.resultMeta).text = buildString {
                product.barcode?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                append("Stock ").append(product.stock.value)
            }
            row.setOnClickListener { viewModel.selectProduct(product) }
            resultsContainer.addView(row)
        }
    }

    private fun handleEvent(event: NewVoucherEvent) {
        when (event) {
            is NewVoucherEvent.Saved -> {
                val m = event.movement
                val msg = if (m.type == MovementType.ENTRADA) getString(R.string.voucher_success_in, m.quantity, m.productName)
                else getString(R.string.voucher_success_out, m.quantity, m.productName)
                parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_MESSAGE to msg))
                parentFragmentManager.popBackStack()
            }
            is NewVoucherEvent.Error -> {
                val msg = when (event.message) {
                    "PRODUCT" -> getString(R.string.voucher_error_product)
                    "QUANTITY" -> getString(R.string.voucher_error_quantity)
                    "FORBIDDEN" -> getString(R.string.role_forbidden_action)
                    "GENERIC" -> getString(R.string.voucher_error_generic)
                    else -> event.message
                }
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
            }
            is NewVoucherEvent.ProductNotFound ->
                Snackbar.make(requireView(), getString(R.string.product_not_found_message, event.barcode), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openScanner() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, BarcodeScannerFragment.newInstance(requestKey = SCAN_REQUEST_KEY))
            .addToBackStack(null)
            .commit()
    }

    private fun reasonLabel(reason: MovementReason): String = when (reason) {
        MovementReason.COMPRA -> getString(R.string.reason_COMPRA)
        MovementReason.DEVOLUCION -> getString(R.string.reason_DEVOLUCION)
        MovementReason.AJUSTE -> getString(R.string.reason_AJUSTE)
        MovementReason.MERMA -> getString(R.string.reason_MERMA)
        MovementReason.OTRO -> getString(R.string.reason_OTRO)
        MovementReason.PEDIDO -> getString(R.string.reason_PEDIDO)
        MovementReason.PEDIDO_EDICION -> getString(R.string.reason_PEDIDO_EDICION)
        MovementReason.PEDIDO_BORRADO -> getString(R.string.reason_PEDIDO_BORRADO)
    }

    companion object {
        private const val ARG_TYPE = "voucher_type"
        private const val ARG_PRODUCT_ID = "voucher_product_id"
        private const val SCAN_REQUEST_KEY = "req_scan_voucher"
        const val RESULT_KEY = "new_voucher_result"
        const val RESULT_MESSAGE = "message"

        fun newInstance(type: MovementType? = null, productId: String? = null): NewVoucherFragment =
            NewVoucherFragment().apply {
                arguments = bundleOf(ARG_TYPE to type?.name, ARG_PRODUCT_ID to productId)
            }
    }
}
