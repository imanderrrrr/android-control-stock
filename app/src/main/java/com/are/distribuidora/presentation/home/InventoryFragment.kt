package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.screenaccess.presentation.ScreenAccessViewModel
import com.are.distribuidora.stockmovement.presentation.NewVoucherFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.presentation.product.AddProductFragment
import com.are.distribuidora.presentation.product.AddStockScannerFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class InventoryFragment : Fragment() {

    private val viewModel: InventoryViewModel by viewModels()

    /**
     * Rol/permisos del usuario (compartido con HomeActivity).
     * - 4.0: EDIT_PRODUCT gobierna nuevo producto, editar y borrar.
     * - 4.1: CREATE_VOUCHER gobierna "Agregar stock" y "Nuevo vale" (mueven inventario).
     */
    private val screenAccessViewModel: ScreenAccessViewModel by activityViewModels()

    private fun canEditProduct(): Boolean =
        screenAccessViewModel.access.value.can(Permission.EDIT_PRODUCT)

    private fun showForbidden() {
        Snackbar.make(requireView(), R.string.role_forbidden_action, Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val progress = view.findViewById<ProgressBar>(R.id.inventoryProgress)
        val empty = view.findViewById<TextView>(R.id.inventoryEmpty)
        val recycler = view.findViewById<RecyclerView>(R.id.inventoryRecycler)
        val inventoryCount = view.findViewById<TextView>(R.id.inventoryCount)

        val searchEditText = view.findViewById<android.widget.EditText>(R.id.searchEditText)
        val newProductButton = view.findViewById<View>(R.id.newProductButton)

        // UI -> ViewModel: el Fragment NO filtra listas, solo envía el query.
        searchEditText.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        newProductButton.setOnClickListener { anchor ->
            if (!canEditProduct()) { showForbidden(); return@setOnClickListener }
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.menu_product_plus, popup.menu)
            // 4.1: "Agregar stock" y "Nuevo vale" mueven inventario → Permission.CREATE_VOUCHER
            // (por defecto solo admin, RolePolicy). "Agregar producto" → EDIT_PRODUCT.
            val access = screenAccessViewModel.access.value
            popup.menu.findItem(R.id.action_add_product).isVisible = access.can(Permission.EDIT_PRODUCT)
            popup.menu.findItem(R.id.action_add_stock).isVisible = access.can(Permission.CREATE_VOUCHER)
            popup.menu.findItem(R.id.action_new_voucher).isVisible = access.can(Permission.CREATE_VOUCHER)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_add_product -> {
                        navigateToAddProduct()
                        true
                    }
                    R.id.action_add_stock -> {
                        if (requireVoucherPermission()) navigateToAddStock()
                        true
                    }
                    R.id.action_new_voucher -> {
                        if (requireVoucherPermission()) navigateToNewVoucher()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // Mensaje de confirmación al volver de "Nuevo vale".
        parentFragmentManager.setFragmentResultListener(NewVoucherFragment.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            bundle.getString(NewVoucherFragment.RESULT_MESSAGE)?.let { msg ->
                Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show()
            }
        }

        val adapter = InventoryAdapter()

        // Vendedor: catálogo en modo consulta (sin botón "+" ni menú de editar/borrar).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                screenAccessViewModel.access.collect { access ->
                    val canEdit = access.can(Permission.EDIT_PRODUCT)
                    newProductButton.visibility = if (canEdit) View.VISIBLE else View.GONE
                    adapter.canEdit = canEdit
                }
            }
        }

        adapter.onProductClick = { productId ->
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    com.are.distribuidora.presentation.productdetail.ProductDetailFragment.newInstance(
                        productId = productId
                    )
                )
                .addToBackStack(null)
                .commit()
        }

        // Conectar el callback de editar
        adapter.onEditClick = { uiModel ->
            if (!canEditProduct()) showForbidden() else parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    com.are.distribuidora.presentation.product.EditProductFragment.newInstance(
                        productId = uiModel.product.id.value
                    )
                )
                .addToBackStack(null)
                .commit()
        }

        // Las acciones de editar/eliminar se manejan ahora desde el menuButton (3 puntos)
        // en cada tarjeta, no desde el click en toda la tarjeta
        
        adapter.onDeleteClick = { uiModel ->
            if (!canEditProduct()) showForbidden() else androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Eliminar Producto")
                .setMessage("¿Estás seguro de que deseas eliminar '${uiModel.product.name}'? Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar") { _, _ ->
                    viewModel.deleteProduct(uiModel.product.id.value)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        recycler.adapter = adapter


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.productCount.collect { count ->
                        inventoryCount.text = if (count != null) "$count productos" else "Gestiona tus productos"
                    }
                }

                // 1. Datos paginados → ÚNICA fuente de submitData.
                //    El estado de sync ya viaja fusionado en cada ProductUiModel
                //    (ver InventoryViewModel.products), así que no hay un segundo
                //    canal con notifyItemChanged manual compitiendo con el differ.
                //    Eso elimina la race "Inconsistency detected. Invalid view
                //    holder adapter position" que crasheaba el inventoryRecycler
                //    cuando un sync invalidaba la tabla products a mitad del refresh.
                launch {
                    viewModel.products.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }

                // 2. Loading State from Adapter
                launch {
                    adapter.loadStateFlow.collect { loadStates ->
                         // Simple loading check (refresh)
                         val isListEmpty = loadStates.refresh is androidx.paging.LoadState.NotLoading && adapter.itemCount == 0
                         val isLoading = loadStates.source.refresh is androidx.paging.LoadState.Loading

                         progress.visibility = if (isLoading) View.VISIBLE else View.GONE
                         recycler.visibility = if (!isLoading && !isListEmpty) View.VISIBLE else View.GONE
                         empty.visibility = if (!isLoading && isListEmpty) View.VISIBLE else View.GONE
                    }
                }

                // 3. Events
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InventoryEvent.SaleSuccess ->
                                Log.i("Inventory", "Venta OK productId=${event.productId} newStock=${event.newStock}")

                            is InventoryEvent.SaleError ->
                                Log.w("Inventory", "Venta FAIL productId=${event.productId} error=${event.message}")

                            is InventoryEvent.StockAddedSuccess -> {
                                Snackbar.make(
                                    requireView(),
                                    getString(R.string.add_stock_success, event.delta),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToAddProduct() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AddProductFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToAddStock() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AddStockScannerFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToNewVoucher() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, NewVoucherFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    /** Segunda barrera del gate (el menú ya oculta la opción): evita el acceso por carrera de estado. */
    private fun requireVoucherPermission(): Boolean {
        val allowed = screenAccessViewModel.access.value.can(Permission.CREATE_VOUCHER)
        if (!allowed) {
            Snackbar.make(requireView(), getString(R.string.voucher_no_permission), Snackbar.LENGTH_LONG).show()
        }
        return allowed
    }
}
