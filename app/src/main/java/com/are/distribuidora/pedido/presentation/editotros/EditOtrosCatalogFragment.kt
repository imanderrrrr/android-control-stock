package com.are.distribuidora.pedido.presentation.editotros

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.pedido.presentation.catalog.CategoryFilter
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogAdapter
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Catálogo de productos para seleccionar ítems al EDITAR un pedido ajeno.
 *
 * Reutiliza el mismo layout, adapter y [OrderCatalogViewModel] que el catálogo de edición
 * de pedidos propios; la única diferencia es que el producto seleccionado va al
 * [EditOtrosPedidoViewModel] (compartido vía activityViewModels), no a CreatePedido/EditPedido.
 */
@AndroidEntryPoint
class EditOtrosCatalogFragment : Fragment(R.layout.fragment_order_catalog) {

    /** Compartido con EditOtrosPedidoFragment (mismo fragmento padre). */
    private val editViewModel: EditOtrosPedidoViewModel by activityViewModels()
    private val catalogViewModel: OrderCatalogViewModel by viewModels()

    @Inject lateinit var logger: Logger

    private val tag = "EDIT_OTROS_CATALOG"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val editSearch     = view.findViewById<EditText>(R.id.editSearch)
        val recyclerView   = view.findViewById<RecyclerView>(R.id.recyclerViewCatalog)
        val chipGroup      = view.findViewById<ChipGroup>(R.id.chipGroupCategories)
        val textEmptyState = view.findViewById<TextView>(R.id.textEmptyState)
        view.findViewById<View>(R.id.cartBar)?.visibility = View.GONE
        view.findViewById<View>(R.id.cartPillCatalog)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.textCatalogCliente)?.text = getString(R.string.order_add_product)

        view.findViewById<View>(R.id.btnBackCatalog).setOnClickListener { parentFragmentManager.popBackStack() }

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

        val adapter = OrderCatalogAdapter(logger)
        adapter.onAddClicked = { product ->
            logger.d(tag, "Producto agregado a pedido ajeno: id=${product.id.value} name=${product.name}")
            editViewModel.addProduct(product)
            parentFragmentManager.popBackStack()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

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
