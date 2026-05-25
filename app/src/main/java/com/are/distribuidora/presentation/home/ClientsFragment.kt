package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.RouteClientsFragment
import com.are.distribuidora.presentation.home.adapter.AccountActivityAdapter
import com.are.distribuidora.presentation.home.adapter.PendingAccountAdapter
import com.are.distribuidora.presentation.home.dialog.AccountDetailDialog
import com.are.distribuidora.presentation.home.dialog.AddPendingAccountDialog
import com.are.distribuidora.presentation.home.dialog.AddRouteDialog
import com.are.distribuidora.presentation.home.model.PendingAccountUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        // ── Routes ───────────────────────────────────────────────────────────
        view.findViewById<View>(R.id.newClientButton).setOnClickListener {
            showAddRouteDialog()
        }

        val carousel = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.routesCarousel)
        val routeAdapter = com.are.distribuidora.presentation.home.adapter.RouteCarouselAdapter { route ->
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    RouteClientsFragment.newInstance(route.id, route.name)
                )
                .addToBackStack(null)
                .commit()
        }
        carousel.adapter = routeAdapter

        // ── Pending Accounts ─────────────────────────────────────────────────
        val recyclerPending  = view.findViewById<RecyclerView>(R.id.recyclerPendingAccounts)
        val textPendingEmpty = view.findViewById<TextView>(R.id.textPendingEmpty)
        val btnAddPending    = view.findViewById<View>(R.id.btnAddPendingAccount)

        val pendingAdapter = PendingAccountAdapter(
            onMarkPaid   = { account -> showMarkPaidConfirmation(account) },
            onDelete     = { account -> showDeleteConfirmation(account) },
            onPhotoClick = { account -> showAccountDetail(account) },
        )
        recyclerPending.layoutManager = LinearLayoutManager(requireContext())
        recyclerPending.adapter = pendingAdapter

        btnAddPending.setOnClickListener {
            // Eliminar instancia previa si quedó en el back-stack (evita "can't show dialog twice")
            childFragmentManager.findFragmentByTag(AddPendingAccountDialog.TAG)
                ?.let { prev ->
                    childFragmentManager.beginTransaction().remove(prev).commitAllowingStateLoss()
                }
            AddPendingAccountDialog().show(childFragmentManager, AddPendingAccountDialog.TAG)
        }

        // ── Activity ─────────────────────────────────────────────────────────
        val sectionActivity = view.findViewById<LinearLayout>(R.id.sectionActivity)
        val recyclerActivity = view.findViewById<RecyclerView>(R.id.recyclerActivity)
        val activityAdapter = AccountActivityAdapter()
        recyclerActivity.layoutManager = LinearLayoutManager(requireContext())
        recyclerActivity.adapter = activityAdapter

        // ── Observe ──────────────────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ClientsViewModel.Event.RouteCreated ->
                                Toast.makeText(requireContext(), R.string.route_created_success, Toast.LENGTH_SHORT).show()
                            is ClientsViewModel.Event.AccountCreated ->
                                Toast.makeText(requireContext(), R.string.pending_created_success, Toast.LENGTH_SHORT).show()
                            is ClientsViewModel.Event.AccountMarkedPaid ->
                                Toast.makeText(requireContext(), R.string.pending_marked_paid, Toast.LENGTH_SHORT).show()
                            is ClientsViewModel.Event.AccountDeleted ->
                                Toast.makeText(requireContext(), R.string.pending_deleted, Toast.LENGTH_SHORT).show()
                            is ClientsViewModel.Event.Error ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.routes.collect { list ->
                        routeAdapter.submitList(list)
                    }
                }

                launch {
                    viewModel.pendingAccounts.collect { accounts ->
                        pendingAdapter.submitList(accounts)
                        textPendingEmpty.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
                        recyclerPending.visibility  = if (accounts.isEmpty()) View.GONE    else View.VISIBLE
                    }
                }

                launch {
                    viewModel.recentActivity.collect { activities ->
                        activityAdapter.submitList(activities)
                        sectionActivity.visibility = if (activities.isEmpty()) View.GONE else View.VISIBLE
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
        AddRouteDialog { name, day ->
            viewModel.createRoute(name, day)
        }.show(childFragmentManager, AddRouteDialog.TAG)
    }

    private fun showMarkPaidConfirmation(account: PendingAccountUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pending_action_mark_paid))
            .setMessage("${account.clientName} — ${account.amountFormatted}")
            .setNegativeButton(R.string.pending_action_cancel, null)
            .setPositiveButton(R.string.pending_action_mark_paid) { _, _ ->
                viewModel.markAccountPaid(account.id)
            }
            .show()
    }

    private fun showDeleteConfirmation(account: PendingAccountUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pending_action_delete))
            .setMessage("${account.clientName} — ${account.amountFormatted}")
            .setNegativeButton(R.string.pending_action_cancel, null)
            .setPositiveButton(R.string.pending_action_delete) { _, _ ->
                viewModel.deleteAccount(account.id)
            }
            .show()
    }

    private fun showAccountDetail(account: PendingAccountUiModel) {
        // Eliminar instancia previa si quedó en el stack
        childFragmentManager.findFragmentByTag(AccountDetailDialog.TAG)
            ?.let { prev -> childFragmentManager.beginTransaction().remove(prev).commitAllowingStateLoss() }

        val dialog = AccountDetailDialog.newInstance(account)
        dialog.onMarkPaid = { showMarkPaidConfirmation(account) }
        dialog.onDelete   = { showDeleteConfirmation(account) }
        dialog.show(childFragmentManager, AccountDetailDialog.TAG)
    }
}
