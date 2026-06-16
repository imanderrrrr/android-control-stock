package com.are.distribuidora.pendingaccount.presentation

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.pendingaccount.presentation.adapter.PendingAccountCardAdapter
import com.are.distribuidora.pendingaccount.presentation.model.PendingAccountUiModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 10 · Cuentas por cobrar (lista). Pantalla dedicada del módulo, accesible desde Clientes.
 */
@AndroidEntryPoint
class PendingAccountsFragment : Fragment(R.layout.fragment_pending_accounts) {

    private val viewModel: PendingAccountsViewModel by viewModels()

    private lateinit var adapter: PendingAccountCardAdapter
    private lateinit var chipAll: TextView
    private lateinit var chipOverdue: TextView
    private lateinit var chipUpcoming: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerAccounts)
        val emptyState = view.findViewById<View>(R.id.emptyState)
        val emptyTitle = view.findViewById<TextView>(R.id.textEmptyTitle)
        val emptySubtitle = view.findViewById<TextView>(R.id.textEmptySubtitle)
        val textTotal = view.findViewById<TextView>(R.id.textTotal)
        val textCount = view.findViewById<TextView>(R.id.textCount)
        val pillOverdue = view.findViewById<TextView>(R.id.pillOverdue)
        chipAll = view.findViewById(R.id.chipAll)
        chipOverdue = view.findViewById(R.id.chipOverdue)
        chipUpcoming = view.findViewById(R.id.chipUpcoming)

        adapter = PendingAccountCardAdapter(
            onClick = { openDetail(it) },
            onMarkPaid = { confirmMarkPaid(it) },
            onDelete = { confirmDelete(it) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.btnViewActivity).setOnClickListener { openActivity() }
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { openForm(null) }

        chipAll.setOnClickListener { viewModel.setFilter(PendingAccountsViewModel.Filter.ALL) }
        chipOverdue.setOnClickListener { viewModel.setFilter(PendingAccountsViewModel.Filter.OVERDUE) }
        chipUpcoming.setOnClickListener { viewModel.setFilter(PendingAccountsViewModel.Filter.UPCOMING) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.accounts.collect { list ->
                        adapter.submitList(list)
                        val empty = list.isEmpty()
                        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                        recycler.visibility = if (empty) View.GONE else View.VISIBLE
                        if (empty) {
                            if (viewModel.filter.value == PendingAccountsViewModel.Filter.ALL) {
                                emptyTitle.setText(R.string.cxc_empty_title)
                                emptySubtitle.visibility = View.VISIBLE
                                emptySubtitle.setText(R.string.cxc_empty_subtitle)
                            } else {
                                emptyTitle.setText(R.string.cxc_empty_filter)
                                emptySubtitle.visibility = View.GONE
                            }
                        }
                    }
                }
                launch {
                    viewModel.summary.collect { s ->
                        textTotal.text = s.totalFormatted
                        textCount.text = when (s.count) {
                            0 -> getString(R.string.cxc_summary_zero)
                            1 -> getString(R.string.cxc_summary_count_one)
                            else -> getString(R.string.cxc_summary_count, s.count)
                        }
                        if (s.overdueCount > 0) {
                            pillOverdue.visibility = View.VISIBLE
                            pillOverdue.text = if (s.overdueCount == 1) getString(R.string.cxc_overdue_pill_one)
                            else getString(R.string.cxc_overdue_pill, s.overdueCount)
                        } else {
                            pillOverdue.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.chipCounts.collect { c ->
                        chipAll.text = getString(R.string.cxc_chip_all, c.all)
                        chipOverdue.text = getString(R.string.cxc_chip_overdue, c.overdue)
                        chipUpcoming.text = getString(R.string.cxc_chip_upcoming, c.upcoming)
                    }
                }
                launch {
                    viewModel.filter.collect { f ->
                        styleChip(chipAll, f == PendingAccountsViewModel.Filter.ALL)
                        styleChip(chipOverdue, f == PendingAccountsViewModel.Filter.OVERDUE)
                        styleChip(chipUpcoming, f == PendingAccountsViewModel.Filter.UPCOMING)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PendingAccountsViewModel.Event.Paid ->
                                toast(R.string.cxc_toast_paid)
                            is PendingAccountsViewModel.Event.Deleted ->
                                toast(R.string.cxc_toast_deleted)
                            is PendingAccountsViewModel.Event.Error ->
                                android.widget.Toast.makeText(requireContext(), event.message, android.widget.Toast.LENGTH_SHORT).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun styleChip(tv: TextView, active: Boolean) {
        tv.setBackgroundResource(
            if (active) R.drawable.bg_cxc_chip_active else R.drawable.bg_cxc_chip_inactive
        )
        tv.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) R.color.text_on_inverse else R.color.text_secondary,
            )
        )
        tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun confirmMarkPaid(account: PendingAccountUiModel) {
        ConfirmActionBottomSheet.newInstance(
            title = getString(R.string.cxc_confirm_paid_title),
            message = getString(R.string.cxc_confirm_paid_msg, account.clientName),
            confirmLabel = getString(R.string.cxc_confirm_paid_yes),
            danger = false,
        ).apply {
            onConfirm = { viewModel.markAccountPaid(account.id) }
        }.show(childFragmentManager, ConfirmActionBottomSheet.TAG)
    }

    private fun confirmDelete(account: PendingAccountUiModel) {
        ConfirmActionBottomSheet.newInstance(
            title = getString(R.string.cxc_confirm_delete_title),
            message = getString(R.string.cxc_confirm_delete_msg, account.clientName, account.amountFormatted),
            confirmLabel = getString(R.string.cxc_confirm_delete_yes),
            danger = true,
        ).apply {
            onConfirm = { viewModel.deleteAccount(account.id) }
        }.show(childFragmentManager, ConfirmActionBottomSheet.TAG)
    }

    private fun openForm(accountId: String?) = push(PendingAccountFormFragment.newInstance(accountId))
    private fun openDetail(account: PendingAccountUiModel) = push(PendingAccountDetailFragment.newInstance(account.id))
    private fun openActivity() = push(PendingAccountActivityFragment())

    private fun push(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(requireContext(), resId, android.widget.Toast.LENGTH_SHORT).show()
    }
}
