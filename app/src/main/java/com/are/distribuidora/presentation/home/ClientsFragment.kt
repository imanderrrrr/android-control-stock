package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.dialog.AddRouteDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint

class ClientsFragment : Fragment() {

    private val viewModel: ClientsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_clients, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup Button
        view.findViewById<View>(R.id.newClientButton).setOnClickListener {
            showAddRouteDialog()
        }

        // Setup Carousel
        val carousel = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.routesCarousel)
        val adapter = com.are.distribuidora.presentation.home.adapter.RouteCarouselAdapter { route ->
            // Handle click later
        }
        carousel.adapter = adapter
        
        // Optional: Add padding/clipToPadding=false for a peek effect if desired
        // carousel.clipToPadding = false
        // carousel.clipChildren = false
        // carousel.offscreenPageLimit = 3

        // Observe Events & Data
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ClientsViewModel.Event.RouteCreated -> {
                                Toast.makeText(requireContext(), R.string.route_created_success, Toast.LENGTH_SHORT).show()
                            }
                            is ClientsViewModel.Event.Error -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.routes.collect { list ->
                        adapter.submitList(list)
                    }
                }
            }
        }
    }

    private fun showAddRouteDialog() {
        AddRouteDialog { name, day ->
            viewModel.createRoute(name, day)
        }.show(childFragmentManager, AddRouteDialog.TAG)
    }
}
