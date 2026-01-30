package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        newProductButton.setOnClickListener {
            // Placeholder: más adelante puede navegar a NewProductFragment.
            Log.i("Inventory", "Nuevo producto (+) presionado")
        }

        val adapter = InventoryAdapter { product ->
            // Punto real de confirmación (placeholder): vender 1 unidad al tocar el producto.
            // Regla: la mutación real ocurre en dominio vía SellProductUseCase.
            viewModel.confirmSale(productId = product.id.value, quantity = 1)
        }
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                        val hasItems = state.products.isNotEmpty()
                        recycler.visibility = if (!state.isLoading && hasItems) View.VISIBLE else View.GONE
                        empty.visibility = if (!state.isLoading && !hasItems) View.VISIBLE else View.GONE

                        adapter.submitList(state.products)
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InventoryEvent.SaleSuccess ->
                                Log.i("Inventory", "Venta OK productId=${event.productId} newStock=${event.newStock}")

                            is InventoryEvent.SaleError ->
                                Log.w("Inventory", "Venta FAIL productId=${event.productId} error=${event.message}")
                        }
                    }
                }
            }
        }
    }
}
