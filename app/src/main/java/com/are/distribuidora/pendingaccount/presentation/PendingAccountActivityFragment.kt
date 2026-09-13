package com.are.distribuidora.pendingaccount.presentation

import android.os.Bundle
import android.view.View
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
import com.are.distribuidora.pendingaccount.presentation.adapter.AccountActivityCardAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 10c · Actividad reciente: cuentas pagadas o eliminadas en los últimos 3 días.
 */
@AndroidEntryPoint
class PendingAccountActivityFragment : Fragment(R.layout.fragment_pending_account_activity) {

    private val viewModel: PendingAccountsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerActivity)
        val empty = view.findViewById<View>(R.id.emptyActivity)
        val adapter = AccountActivityCardAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activity.collect { list ->
                    adapter.submitList(list)
                    empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
