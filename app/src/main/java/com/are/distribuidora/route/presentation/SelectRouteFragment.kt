package com.are.distribuidora.route.presentation

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.are.distribuidora.R
import com.are.distribuidora.client.presentation.SelectClientFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class SelectRouteFragment : Fragment(R.layout.fragment_select_route) {

    private val viewModel: SelectRouteViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val dateSubtitle = view.findViewById<TextView>(R.id.dateSubtitle)
        val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val emptyState = view.findViewById<TextView>(R.id.emptyState)
        val errorState = view.findViewById<TextView>(R.id.errorState)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        val btnFreeRoute = view.findViewById<MaterialButton>(R.id.btnFreeRoute)
        val titleText = view.findViewById<TextView>(R.id.titleText)

        val setActiveMode = (arguments?.getString(ARG_MODE) ?: MODE_CREATE_ORDER) == MODE_SET_ACTIVE

        dateSubtitle.text = formatToday()

        if (setActiveMode) {
            titleText.text = "Elige tu ruta activa"
            btnFreeRoute.visibility = View.GONE
        } else {
            btnFreeRoute.setOnClickListener {
                Toast.makeText(requireContext(), "Ruta libre — próximamente", Toast.LENGTH_SHORT).show()
            }
        }

        val adapter = SelectRouteAdapter { route ->
            if (setActiveMode) {
                // Fijar como ruta activa de hoy y volver al dashboard.
                viewModel.setActiveRoute(route.id)
                parentFragmentManager.popBackStack()
            } else {
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                    .replace(
                        R.id.fragmentContainer,
                        SelectClientFragment.newInstance(route.id)
                    )
                    .addToBackStack(null)
                    .commit()
            }
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

    private fun formatToday(): String {
        val locale = Locale("es", "GT")
        val today = LocalDate.now()
        val datePart = today.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        return "$datePart · jornada matutina"
    }

    companion object {
        const val MODE_CREATE_ORDER = "create_order"
        const val MODE_SET_ACTIVE = "set_active"
        private const val ARG_MODE = "mode"

        fun newInstance(mode: String = MODE_CREATE_ORDER) = SelectRouteFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode) }
        }
    }
}
