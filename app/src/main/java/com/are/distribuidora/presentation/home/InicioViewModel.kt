package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.usecase.ObserveSessionUseCase
import com.are.distribuidora.client.domain.usecase.ObserveClientsByRouteUseCase
import com.are.distribuidora.domain.pedido.usecase.ObserveAllPedidosUseCase
import com.are.distribuidora.pendingaccount.domain.usecase.ObserveClientDebtsUseCase
import com.are.distribuidora.domain.product.GetProductCountUseCase
import com.are.distribuidora.route.data.local.ActiveRouteStore
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dashboard "Inicio". La RUTA ACTIVA la elige el vendedor (persistida por hoy en [ActiveRouteStore]);
 * el día arranca vacío hasta que escoge una. Datos reales:
 * - Ruta activa elegida → total de clientes + "atendidos hoy" (clientes distintos de la ruta con
 *   un PEDIDO de hoy) + próximos clientes con badge DEBE (cuentas pendientes).
 * - Conteo de productos (catálogo). Saludo + nombre (sesión).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InicioViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val observeClientsByRouteUseCase: ObserveClientsByRouteUseCase,
    private val observeAllPedidosUseCase: ObserveAllPedidosUseCase,
    private val getProductCountUseCase: GetProductCountUseCase,
    private val observeClientDebtsUseCase: ObserveClientDebtsUseCase,
    private val activeRouteStore: ActiveRouteStore,
) : ViewModel() {

    data class ProximoCliente(
        val initials: String,
        val name: String,
        val address: String,
        val debe: Boolean,
        val saldoText: String? = null,
    )

    data class UiState(
        val hasRoute: Boolean = false,
        val activeRouteId: String? = null,
        val activeRouteName: String? = null,
        val totalClients: Int = 0,
        val atendidosHoy: Int = 0,
        val progressPct: Int = 0,
        val proximos: List<ProximoCliente> = emptyList(),
        val productCount: Int? = null,
    )

    val greetingName: StateFlow<String> = observeSessionUseCase()
        .map { session -> deriveName(session?.email) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Vendedor")

    private val today = LocalDate.now().toString() // "yyyy-MM-dd"
    private val productCount = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<UiState> =
        activeRouteStore.observeActiveRouteId(today)
            .flatMapLatest { routeId -> routeStateFlow(routeId) }
            .combine(productCount) { state, count -> state.copy(productCount = count) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            runCatching { getProductCountUseCase() }.getOrNull()?.let { productCount.value = it }
        }
    }

    /** Desactivar la ruta activa por hoy → el dashboard vuelve al estado vacío. */
    fun clearActiveRoute() {
        viewModelScope.launch { activeRouteStore.clear() }
    }

    private suspend fun routeStateFlow(routeId: String?): Flow<UiState> {
        val route = routeId?.let { id -> loadRoutes().firstOrNull { it.id == id } }
            ?: return flowOf(UiState(hasRoute = false))

        return combine(
            observeClientsByRouteUseCase(route.id),
            observeClientDebtsUseCase(),
            observeAllPedidosUseCase(today),
        ) { clients, debts, pedidosHoy ->
            val attendedIds = pedidosHoy.asSequence()
                .map { it.pedido }
                .filter { it.routeId == route.id }
                .mapNotNull { it.clienteId?.takeIf { id -> id.isNotBlank() } }
                .toHashSet()

            val total = clients.size
            val atendidos = clients.count { it.id in attendedIds }
            val pct = if (total > 0) (atendidos * 100) / total else 0

            UiState(
                hasRoute = true,
                activeRouteId = route.id,
                activeRouteName = route.name,
                totalClients = total,
                atendidosHoy = atendidos,
                progressPct = pct,
                proximos = clients.take(2).map { c ->
                    val debt = debts[c.id]
                    ProximoCliente(
                        initials = initialsOf(c.name),
                        name = c.name,
                        address = c.address?.takeIf { it.isNotBlank() } ?: "Sin dirección",
                        debe = debt != null,
                        saldoText = debt?.let { "Saldo ${it.formattedTotal}" },
                    )
                },
            )
        }
    }

    private suspend fun loadRoutes(): List<Route> {
        var routes = emptyList<Route>()
        getRoutesUseCase().onSuccess { routes = it }
        return routes
    }

    private fun deriveName(email: String?): String {
        if (email.isNullOrBlank()) return "Vendedor"
        val local = email.substringBefore("@")
        val letters = local.takeWhile { it.isLetter() }
        val base = if (letters.length >= 2) letters else local
        return base.replaceFirstChar { it.uppercase() }
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].first().toString() + parts[1].first()).uppercase()
        }
    }
}
