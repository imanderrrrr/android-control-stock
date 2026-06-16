package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.RouteClientsFragment
import com.are.distribuidora.pendingaccount.presentation.PendingAccountsFragment
import com.are.distribuidora.presentation.home.adapter.RouteCarouselAdapter
import com.are.distribuidora.presentation.home.dialog.AddRouteDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Pestaña "Clientes": carrusel de rutas + tarjeta de acceso a Cuentas por cobrar.
 * El módulo de cobros vive ahora en su propia pantalla (PendingAccountsFragment).
 */
@AndroidEntryPoint
class ClientsFragment : Fragment() {

    private val viewModel: ClientsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_clients, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.newClientButton).setOnClickListener { showAddRouteDialog() }

        val carousel = view.findViewById<ViewPager2>(R.id.routesCarousel)
        val routeAdapter = RouteCarouselAdapter { route ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RouteClientsFragment.newInstance(route.id, route.name))
                .addToBackStack(null)
                .commit()
        }
        carousel.adapter = routeAdapter

        val pendingBadge = view.findViewById<TextView>(R.id.pendingCountBadge)
        view.findViewById<View>(R.id.cardPendingEntry).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                .replace(R.id.fragmentContainer, PendingAccountsFragment())
                .addToBackStack(null)
                .commit()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ClientsViewModel.Event.RouteCreated ->
                                Toast.makeText(requireContext(), R.string.route_created_success, Toast.LENGTH_SHORT).show()
                            is ClientsViewModel.Event.Error ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.routes.collect { routeAdapter.submitList(it) }
                }
                launch {
                    viewModel.pendingCount.collect { count ->
                        if (count > 0) {
                            pendingBadge.visibility = View.VISIBLE
                            pendingBadge.text = count.toString()
                        } else {
                            pendingBadge.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRoutes()
    }

    private fun showAddRouteDialog() {
        AddRouteDialog { name, day -> viewModel.createRoute(name, day) }
            .show(childFragmentManager, AddRouteDialog.TAG)
    }
}
