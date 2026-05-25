package com.are.distribuidora.pedido.presentation.catalog

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.map
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.cart.OrderCartFragment
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.are.distribuidora.pedido.presentation.catalog.ui.GridSpacingItemDecoration
import com.are.distribuidora.pedido.presentation.detail.ProductDetailOrderFragment
import com.are.distribuidora.presentation.product.EditProductFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OrderCatalogFragment : Fragment(R.layout.fragment_order_catalog) {

    private val flowViewModel: CreatePedidoFlowViewModel by activityViewModels()
    private val viewModel: OrderCatalogViewModel by viewModels()

    private val tag = "ORDER_CATALOG"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar          = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val editSearch       = view.findViewById<TextInputEditText>(R.id.editSearch)
        val recyclerView     = view.findViewById<RecyclerView>(R.id.recyclerViewCatalog)
        val chipGroup        = view.findViewById<ChipGroup>(R.id.chipGroupCategories)
        val textEmptyState   = view.findViewById<TextView>(R.id.textEmptyState)
        val fabCart          = view.findViewById<FloatingActionButton>(R.id.fabCart)

        // ── Chips → map chip id to CategoryFilter ───────────────────────────
        val chipCategoryMap: Map<Int, CategoryFilter> = mapOf(
            R.id.chipTodos          to CategoryFilter.TODOS,
            R.id.chipAlimentos      to CategoryFilter.ALIMENTOS,
            R.id.chipBebidas        to CategoryFilter.BEBIDAS,
            R.id.chipHogarLimpieza  to CategoryFilter.HOGAR_Y_LIMPIEZA,
            R.id.chipSnacks         to CategoryFilter.SNACKS,
            R.id.chipHigiene        to CategoryFilter.HIGIENE,
            R.id.chipVarios         to CategoryFilter.VARIOS,
        )

        val routeId   = flowViewModel.routeId.value
        val selection = flowViewModel.clienteSelection.value

        if (routeId.isNullOrBlank() || selection == null) {
            Toast.makeText(requireContext(), "No hay cliente/ruta seleccionados", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        Log.d(tag, "Catalog opened. routeId=$routeId selection=$selection")

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // ── FAB carrito ──────────────────────────────────────────────────────
        fabCart.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, OrderCartFragment(), "ORDER_CART")
                .addToBackStack("FLOW_CATALOG")
                .commit()
        }

        // ── Search ──────────────────────────────────────────────────────────
        editSearch.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        // ── Chips listener ───────────────────────────────────────────────────
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val category  = chipCategoryMap[checkedId] ?: CategoryFilter.TODOS
            viewModel.onCategorySelected(category)
            Log.d(tag, "Category selected: ${category.label}")
        }

        // ── Adapter + RecyclerView ───────────────────────────────────────────
        val adapter = OrderCatalogAdapter()

        adapter.onProductClicked = { product ->
            Log.d(tag, "Product clicked: id=${product.id.value} name=${product.name}")
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ProductDetailOrderFragment.newInstance(product), "PRODUCT_DETAIL")
                .addToBackStack("FLOW_CATALOG")
                .commit()
        }

        adapter.onProductLongPressed = { product ->
            Log.d(tag, "Product long-pressed: id=${product.id.value} name=${product.name}")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(product.name)
                .setItems(arrayOf("Editar producto")) { _, _ ->
                    parentFragmentManager.beginTransaction()
                        .replace(
                            R.id.fragmentContainer,
                            EditProductFragment.newInstance(productId = product.id.value),
                            "EDIT_PRODUCT_FROM_CATALOG"
                        )
                        .addToBackStack("FLOW_CATALOG")
                        .commit()
                }
                .show()
        }
        adapter.onAddClicked = { product ->
            Log.d(tag, "Add to cart: id=${product.id.value} name=${product.name}")
            flowViewModel.add(product)
        }
        adapter.onIncrementClicked = { productId ->
            flowViewModel.increment(productId)
        }
        adapter.onDecrementClicked = { productId ->
            flowViewModel.decrement(productId)
        }

        val spanCount = 2
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        recyclerView.adapter = adapter

        val spacingPx = (resources.displayMetrics.density * 12).toInt()
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(
                GridSpacingItemDecoration(
                    spanCount    = spanCount,
                    spacingPx    = spacingPx,
                    includeEdge  = false,
                )
            )
        }

        // ── Observadores ────────────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 1. Datos paginados + carrito combinados → único submitData()
                //
                //    Esta es la solución a la race condition que causaba el
                //    crash "Inconsistency detected" del RecyclerView. Antes
                //    teníamos dos coroutines tocando el adapter (submitData
                //    + updateCartQuantities) que podían colisionar con el
                //    layout pass. Ahora combinamos los dos flows en uno y
                //    dejamos que DiffUtil maneje todo atómicamente.
                launch {
                    viewModel.products
                        .combine(flowViewModel.cartItems) { pagingData, cartMap ->
                            pagingData.map { product ->
                                ProductWithQty(
                                    product = product,
                                    qty = cartMap[product.id.value]?.quantity ?: 0,
                                )
                            }
                        }
                        .collectLatest { transformed ->
                            adapter.submitData(transformed)
                        }
                }

                // 2. LoadState → mostrar/ocultar empty state
                launch {
                    adapter.loadStateFlow.collect { states ->
                        val refresh = states.refresh
                        when {
                            refresh is LoadState.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    refresh.error.message ?: "Error cargando catálogo",
                                    Toast.LENGTH_SHORT
                                ).show()
                                textEmptyState.visibility = View.GONE
                                recyclerView.visibility   = View.VISIBLE
                            }
                            refresh is LoadState.NotLoading && adapter.itemCount == 0 -> {
                                textEmptyState.visibility = View.VISIBLE
                                recyclerView.visibility   = View.GONE
                            }
                            else -> {
                                textEmptyState.visibility = View.GONE
                                recyclerView.visibility   = View.VISIBLE
                            }
                        }
                    }
                }

                // 3. Sincronizar chip seleccionado con estado del ViewModel
                launch {
                    viewModel.selectedCategory.collect { category ->
                        val chipId = chipCategoryMap.entries
                            .firstOrNull { it.value == category }?.key
                        if (chipId != null) {
                            val chip = view.findViewById<Chip>(chipId)
                            if (chip != null && !chip.isChecked) {
                                chip.isChecked = true
                            }
                        }
                    }
                }
            }
        }
    }
}

