package com.are.distribuidora.client.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.presentation.mapper.toUiModel
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.usecase.ObserveAllPedidosUseCase
import com.are.distribuidora.pendingaccount.domain.usecase.ObserveClientDebtsUseCase
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Chips de la pantalla Seleccionar cliente (Pencil 05). */
enum class ClientFilter { MI_RUTA, PENDIENTES, CON_SALDO }

sealed class SelectClientUiState {
    object Idle : SelectClientUiState()
    object Loading : SelectClientUiState()
    data class Success(val clients: List<ClientUiModel>) : SelectClientUiState()
    object Empty : SelectClientUiState()
    object Error : SelectClientUiState()
}

sealed class RoutesUiState {
    object Loading : RoutesUiState()
    data class Success(val routes: List<Route>) : RoutesUiState()
    data class Error(val message: String? = null) : RoutesUiState()
}

/** Conteos para las etiquetas de los chips ("Mi ruta · 28"). */
data class ChipCounts(val miRuta: Int = 0, val pendientes: Int = 0, val conSaldo: Int = 0)

private sealed class RawClients {
    object Idle : RawClients()
    object Loading : RawClients()
    data class Loaded(val clients: List<Client>) : RawClients()
    object Empty : RawClients()
    object Error : RawClients()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SelectClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val observeClientDebtsUseCase: ObserveClientDebtsUseCase,
    private val observeAllPedidosUseCase: ObserveAllPedidosUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_ROUTE_ID = "selectedRouteId"
    }

    private val today = LocalDate.now().toString() // "yyyy-MM-dd"

    private val _routesState = MutableStateFlow<RoutesUiState>(RoutesUiState.Loading)
    val routesState: StateFlow<RoutesUiState> = _routesState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(ClientFilter.MI_RUTA)
    val filter: StateFlow<ClientFilter> = _filter.asStateFlow()

    private val _raw = MutableStateFlow<RawClients>(RawClients.Idle)

    private var searchJob: Job? = null

    val selectedRouteId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_SELECTED_ROUTE_ID, null)

    /** Deuda por cliente (cuentas por cobrar) — fuente única reutilizable. */
    private val debts = observeClientDebtsUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Clientes ya atendidos hoy en la ruta seleccionada (tienen pedido de hoy). */
    private val attendedIds: StateFlow<Set<String>> = selectedRouteId
        .flatMapLatest { routeId ->
            if (routeId.isNullOrBlank()) flowOf(emptySet<String>())
            else observeAllPedidosUseCase(today).map { list ->
                list.asSequence()
                    .map { it.pedido }
                    .filter { it.routeId == routeId }
                    .mapNotNull { it.clienteId?.takeIf { id -> id.isNotBlank() } }
                    .toHashSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val chipCounts: StateFlow<ChipCounts> =
        combine(_raw, debts, attendedIds) { raw, debtMap, attended ->
            val list = (raw as? RawClients.Loaded)?.clients ?: emptyList()
            ChipCounts(
                miRuta = list.size,
                pendientes = list.count { it.id !in attended },
                conSaldo = list.count { debtMap.containsKey(it.id) },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChipCounts())

    val uiState: StateFlow<SelectClientUiState> =
        combine(_raw, debts, attendedIds, _filter) { raw, debtMap, attended, filter ->
            when (raw) {
                RawClients.Idle -> SelectClientUiState.Idle
                RawClients.Loading -> SelectClientUiState.Loading
                RawClients.Error -> SelectClientUiState.Error
                RawClients.Empty -> SelectClientUiState.Empty
                is RawClients.Loaded -> {
                    val mapped = raw.clients.map {
                        it.toUiModel(debt = debtMap[it.id], attended = it.id in attended)
                    }
                    val filtered = when (filter) {
                        ClientFilter.MI_RUTA -> mapped
                        ClientFilter.PENDIENTES -> mapped.filter { !it.attended }
                        ClientFilter.CON_SALDO -> mapped.filter { it.debtCents != null }
                    }
                    if (filtered.isEmpty()) SelectClientUiState.Empty
                    else SelectClientUiState.Success(filtered)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SelectClientUiState.Idle)

    init {
        loadRoutes()

        _searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .onEach { rawQuery ->
                val routeId = selectedRouteId.value
                if (routeId.isNullOrBlank()) return@onEach

                val query = rawQuery.trim()
                // Regla UX: mínimo 2 caracteres para buscar. Si está vacío, listamos inicial.
                if (query.isNotEmpty() && query.length < 2) return@onEach

                performSearch(routeId, query)
            }
            .launchIn(viewModelScope)
    }

    fun loadRoutes() {
        viewModelScope.launch {
            _routesState.value = RoutesUiState.Loading
            when (val result = getRoutesUseCase()) {
                is Result.Success -> _routesState.value = RoutesUiState.Success(result.value)
                is Result.Error -> _routesState.value = RoutesUiState.Error(result.failure.toString())
            }
        }
    }

    /**
     * Llamar cuando el usuario selecciona una ruta.
     * Persiste en SavedStateHandle, resetea búsqueda y carga clientes iniciales (LIMIT 50).
     */
    fun onRouteSelected(routeId: String) {
        val current = selectedRouteId.value
        if (current == routeId) return

        savedStateHandle[KEY_SELECTED_ROUTE_ID] = routeId
        resetSearch()
        loadInitialData(routeId)
    }

    /** Compat: permite preseleccionar route desde args. */
    fun ensureRouteSelected(routeId: String) {
        if (selectedRouteId.value.isNullOrBlank()) {
            onRouteSelected(routeId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: ClientFilter) {
        _filter.value = filter
    }

    fun resetSearch() {
        _searchQuery.value = ""
    }

    private fun loadInitialData(routeId: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _raw.value = RawClients.Loading
            when (val result = clientRepository.searchClientsByRoute(routeId, "")) {
                is Result.Success ->
                    _raw.value = if (result.value.isEmpty()) RawClients.Empty
                    else RawClients.Loaded(result.value)
                is Result.Error -> _raw.value = RawClients.Error
            }
        }
    }

    private fun performSearch(routeId: String, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _raw.value = RawClients.Loading
            when (val result = clientRepository.searchClientsByRoute(routeId, query)) {
                is Result.Success ->
                    _raw.value = if (result.value.isEmpty()) RawClients.Empty
                    else RawClients.Loaded(result.value)
                is Result.Error -> _raw.value = RawClients.Error
            }
        }
    }
}
