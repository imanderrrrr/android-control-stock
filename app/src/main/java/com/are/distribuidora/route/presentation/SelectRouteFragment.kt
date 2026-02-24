package com.are.distribuidora.route.presentation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.SelectClientFragment

@AndroidEntryPoint
class SelectRouteFragment : Fragment(R.layout.fragment_select_route) {

    private val viewModel: SelectRouteViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val emptyState = view.findViewById<android.widget.TextView>(R.id.emptyState)
        val errorState = view.findViewById<android.widget.TextView>(R.id.errorState)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val adapter = SelectRouteAdapter { route ->
            // Navigate to SelectClientFragment with routeId
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    SelectClientFragment.newInstance(route.id)
                )
                .addToBackStack(null) // Generic backstack entry
                .commit()
        }
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SelectRouteUiState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.GONE
                            recyclerView.visibility = View.GONE
                        }
                        is SelectRouteUiState.Empty -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.VISIBLE
                            errorState.visibility = View.GONE
                            recyclerView.visibility = View.GONE
                        }
                        is SelectRouteUiState.Error -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                        is SelectRouteUiState.Success -> {
                            progressBar.visibility = View.GONE
                            emptyState.visibility = View.GONE
                            errorState.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            adapter.submitList(state.routes)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance() = SelectRouteFragment()
    }
}
