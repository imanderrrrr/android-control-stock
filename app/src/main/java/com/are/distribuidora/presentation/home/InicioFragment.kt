package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Dashboard "Inicio" — pantalla 04 del rediseño "Bosque Pro".
 * La ruta activa la elige el vendedor: el panel alterna entre estado CON ruta y estado VACÍO.
 */
@AndroidEntryPoint
class InicioFragment : Fragment(R.layout.fragment_inicio) {

    private val viewModel: InicioViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val greetingText = view.findViewById<TextView>(R.id.greetingText)
        val nameText = view.findViewById<TextView>(R.id.nameText)
        val avatarInitials = view.findViewById<TextView>(R.id.avatarInitials)
        val activeRouteGroup = view.findViewById<View>(R.id.activeRouteGroup)
        val emptyRouteGroup = view.findViewById<View>(R.id.emptyRouteGroup)
        val activeRouteName = view.findViewById<TextView>(R.id.activeRouteName)
        val activeRouteProgress = view.findViewById<TextView>(R.id.activeRouteProgress)
        val progressFill = view.findViewById<View>(R.id.progressFill)
        val progressEmpty = view.findViewById<View>(R.id.progressEmpty)
        val catalogSubtitle = view.findViewById<TextView>(R.id.catalogSubtitle)
        val clientesSubtitle = view.findViewById<TextView>(R.id.clientesSubtitle)
        val proximosHeader = view.findViewById<View>(R.id.proximosHeader)
        val client1Card = view.findViewById<MaterialCardView>(R.id.client1Card)
        val client1Initials = view.findViewById<TextView>(R.id.client1Initials)
        val client1Name = view.findViewById<TextView>(R.id.client1Name)
        val client1Address = view.findViewById<TextView>(R.id.client1Address)
        val client1Badge = view.findViewById<TextView>(R.id.client1Badge)
        val client2Card = view.findViewById<MaterialCardView>(R.id.client2Card)
        val client2Initials = view.findViewById<TextView>(R.id.client2Initials)
        val client2Name = view.findViewById<TextView>(R.id.client2Name)
        val client2Address = view.findViewById<TextView>(R.id.client2Address)
        val client2Badge = view.findViewById<TextView>(R.id.client2Badge)

        greetingText.text = greetingForNow()

        val home = activity as? HomeActivity

        view.findViewById<MaterialButton>(R.id.btnContinuarRuta).setOnClickListener {
            viewModel.uiState.value.activeRouteId?.let { home?.continueActiveRoute(it) }
        }
        view.findViewById<TextView>(R.id.btnCambiarRuta).setOnClickListener { home?.openSelectActiveRoute() }
        view.findViewById<MaterialButton>(R.id.btnElegirRuta).setOnClickListener { home?.openSelectActiveRoute() }
        view.findViewById<TextView>(R.id.btnDesactivarRuta).setOnClickListener { viewModel.clearActiveRoute() }

        view.findViewById<MaterialCardView>(R.id.cardNuevoPedido).setOnClickListener { home?.startCreateOrder() }
        view.findViewById<MaterialCardView>(R.id.cardCatalogo).setOnClickListener { home?.goToTab(R.id.nav_inventory) }
        view.findViewById<MaterialCardView>(R.id.cardClientes).setOnClickListener { home?.goToTab(R.id.nav_clients) }
        view.findViewById<MaterialCardView>(R.id.cardReportes).setOnClickListener { home?.goToTab(R.id.nav_reportes) }
        view.findViewById<TextView>(R.id.verTodos).setOnClickListener { home?.goToTab(R.id.nav_clients) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.greetingName.collect { name ->
                        nameText.text = name
                        avatarInitials.text = name.take(2).uppercase()
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        activeRouteGroup.visibility = if (state.hasRoute) View.VISIBLE else View.GONE
                        emptyRouteGroup.visibility = if (state.hasRoute) View.GONE else View.VISIBLE

                        if (state.hasRoute) {
                            activeRouteName.text = state.activeRouteName ?: "—"
                            activeRouteProgress.text =
                                if (state.totalClients > 0)
                                    "${state.atendidosHoy} de ${state.totalClients} clientes atendidos hoy · ${state.progressPct}%"
                                else "Sin clientes en esta ruta"
                            setProgress(progressFill, progressEmpty, state.progressPct)
                        }

                        catalogSubtitle.text = state.productCount?.let { "$it productos" } ?: "Ver productos"
                        clientesSubtitle.text =
                            if (state.hasRoute) "${state.totalClients} en esta ruta" else "Ver cartera"

                        bindClient(client1Card, client1Initials, client1Name, client1Address, client1Badge, state.proximos.getOrNull(0))
                        bindClient(client2Card, client2Initials, client2Name, client2Address, client2Badge, state.proximos.getOrNull(1))
                        proximosHeader.visibility = if (state.proximos.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setProgress(fill: View, empty: View, pct: Int) {
        (fill.layoutParams as LinearLayout.LayoutParams).weight = pct.toFloat()
        (empty.layoutParams as LinearLayout.LayoutParams).weight = (100 - pct).toFloat()
        fill.requestLayout()
        empty.requestLayout()
    }

    private fun bindClient(
        card: View,
        initials: TextView,
        name: TextView,
        address: TextView,
        badge: TextView,
        c: InicioViewModel.ProximoCliente?,
    ) {
        if (c == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        initials.text = c.initials
        name.text = c.name
        address.text = c.address
        badge.visibility = if (c.debe) View.VISIBLE else View.GONE
    }

    private fun greetingForNow(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días,"
            in 12..18 -> "Buenas tardes,"
            else -> "Buenas noches,"
        }
    }
}
