package com.are.distribuidora.client.presentation

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RouteClientsFragment : Fragment() {

    private val viewModel: RouteClientsViewModel by viewModels()

    private var routeId: String = ""
    private var routeName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeId = requireArguments().getString(ARG_ROUTE_ID).orEmpty()
        routeName = requireArguments().getString(ARG_ROUTE_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_route_clients, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerView)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val errorState = view.findViewById<TextView>(R.id.errorState)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val fab = view.findViewById<FloatingActionButton>(R.id.fab)

        toolbar.title = routeName
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val adapter = RouteClientsAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        adapter.onDeleteClick = { client ->
            showDeleteDialog(client)
        }

        adapter.onEditClick = { client ->
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    EditClientFragment.newInstance(clientId = client.id)
                )
                .addToBackStack(null)
                .commit()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.searchQuery.value = s?.toString().orEmpty()
            }
        })

        fab.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    CreateClientFragment.newInstance(routeId = routeId, routeName = routeName)
                )
                .addToBackStack(null)
                .commit()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is RouteClientsUiState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.GONE
                            recycler.visibility = View.GONE
                        }

                        is RouteClientsUiState.Empty -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.VISIBLE
                            errorState.visibility = View.GONE
                            recycler.visibility = View.GONE
                            adapter.submitList(emptyList())
                        }

                        is RouteClientsUiState.Success -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.GONE
                            recycler.visibility = View.VISIBLE
                            // CRÍTICO: crear nueva referencia con toList()
                            adapter.submitList(state.clients.toList())
                        }

                        is RouteClientsUiState.Error -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.VISIBLE
                            recycler.visibility = View.GONE
                            errorState.text = state.message
                            adapter.submitList(emptyList())
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (routeId.isNotBlank()) {
            viewModel.load(routeId)
        }
    }

    private fun showDeleteDialog(client: com.are.distribuidora.client.presentation.model.ClientUiModel) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar cliente")
            .setMessage("¿Estás seguro de que deseas eliminar a ${client.name}?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteClient(client.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        private const val ARG_ROUTE_ID = "routeId"
        private const val ARG_ROUTE_NAME = "routeName"

        fun newInstance(routeId: String, routeName: String): RouteClientsFragment {
            return RouteClientsFragment().apply {
                arguments = bundleOf(
                    ARG_ROUTE_ID to routeId,
                    ARG_ROUTE_NAME to routeName
                )
            }
        }
    }
}
