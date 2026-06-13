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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = view.findViewById<ProgressBar>(R.id.inventoryProgress)
        val empty = view.findViewById<TextView>(R.id.inventoryEmpty)
        val recycler = view.findViewById<RecyclerView>(R.id.inventoryRecycler)

        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)
        val newProductButton = view.findViewById<MaterialButton>(R.id.newProductButton)

        // UI -> ViewModel: el Fragment NO filtra listas, solo envía el query.
        searchEditText.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }

        newProductButton.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.menu_product_plus, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_add_product -> {
                        navigateToAddProduct()
                        true
                    }
                    R.id.action_add_stock -> {
                        navigateToAddStock()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        val adapter = InventoryAdapter()

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
            parentFragmentManager.beginTransaction()
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
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
                // 1a. Datos paginados → ÚNICA fuente de submitData (búsqueda).
                launch {
                    viewModel.products.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }

                // 1b. Estados de sincronización → canal separado.
                //     submitSyncStatuses hace notifyItemChanged puntual con
                //     PAYLOAD_SYNC; nunca re-dispara submitData. Esto elimina la
                //     race "Inconsistency detected. Invalid view holder adapter
                //     position" que crasheaba el inventoryRecycler cuando el sync
                //     de productos emitía estados a mitad del layout pass.
                launch {
                    viewModel.syncStatuses.collect { statuses ->
                        adapter.submitSyncStatuses(statuses)
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
}
