package com.are.distribuidora.pedido.presentation.edit

import android.os.Bundle
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pedido.presentation.catalog.CategoryFilter
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogAdapter
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogViewModel
import com.are.distribuidora.pedido.presentation.catalog.ui.GridSpacingItemDecoration
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Catálogo de productos para seleccionar ítems al editar un pedido.
 * Reutiliza el mismo layout y ViewModel de catálogo, pero en lugar de
 * agregar al CreatePedidoFlowViewModel, agrega al EditPedidoViewModel.
 *
 * NOTA: NO usa CreatePedidoFlowViewModel — el carrito de creación no se toca.
 */
@AndroidEntryPoint
class EditPedidoCatalogFragment : Fragment(R.layout.fragment_order_catalog) {

    /** Shared con EditPedidoFragment (mismo fragmento padre). */
    private val editViewModel: EditPedidoViewModel by activityViewModels()
    private val catalogViewModel: OrderCatalogViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar        = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val editSearch     = view.findViewById<TextInputEditText>(R.id.editSearch)
        val recyclerView   = view.findViewById<RecyclerView>(R.id.recyclerViewCatalog)
        val chipGroup      = view.findViewById<ChipGroup>(R.id.chipGroupCategories)
        val textEmptyState = view.findViewById<TextView>(R.id.textEmptyState)
        // Ocultar FAB carrito — no aplica en el flujo de edición
        view.findViewById<View>(R.id.fabCart)?.visibility = View.GONE

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val chipCategoryMap: Map<Int, CategoryFilter> = mapOf(
            R.id.chipTodos          to CategoryFilter.TODOS,
            R.id.chipAlimentos      to CategoryFilter.ALIMENTOS,
            R.id.chipBebidas        to CategoryFilter.BEBIDAS,
            R.id.chipHogarLimpieza  to CategoryFilter.HOGAR_Y_LIMPIEZA,
            R.id.chipSnacks         to CategoryFilter.SNACKS,
            R.id.chipHigiene        to CategoryFilter.HIGIENE,
            R.id.chipVarios         to CategoryFilter.VARIOS,
        )

        editSearch.doAfterTextChanged { text ->
            catalogViewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val category  = chipCategoryMap[checkedId] ?: CategoryFilter.TODOS
            catalogViewModel.onCategorySelected(category)
        }

        val adapter = OrderCatalogAdapter()
        adapter.onAddClicked = { product ->
            editViewModel.addProduct(product)
            // Volver a la pantalla de edición tras agregar
            parentFragmentManager.popBackStack()
        }

        val spanCount = 2
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        recyclerView.adapter = adapter

        val spacingPx = (resources.displayMetrics.density * 12).toInt()
        if (recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(
                GridSpacingItemDecoration(spanCount = spanCount, spacingPx = spacingPx, includeEdge = false)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    catalogViewModel.products.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }

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

                launch {
                    catalogViewModel.selectedCategory.collect { category ->
                        val chipId = chipCategoryMap.entries
                            .firstOrNull { it.value == category }?.key
                        if (chipId != null) {
                            view.findViewById<Chip>(chipId)?.let { chip ->
                                if (!chip.isChecked) chip.isChecked = true
                            }
                        }
                    }
                }
            }
        }
    }
}

