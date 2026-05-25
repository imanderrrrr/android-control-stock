package com.are.distribuidora.client.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.client.presentation.mapper.toUiModel
import com.are.distribuidora.client.presentation.model.ClientUiModel
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@OptIn(FlowPreview::class)
@HiltViewModel
class SelectClientViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_ROUTE_ID = "selectedRouteId"
    }

    private val _uiState = MutableStateFlow<SelectClientUiState>(SelectClientUiState.Idle)
    val uiState: StateFlow<SelectClientUiState> = _uiState.asStateFlow()

    private val _routesState = MutableStateFlow<RoutesUiState>(RoutesUiState.Loading)
    val routesState: StateFlow<RoutesUiState> = _routesState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var searchJob: Job? = null

    val selectedRouteId: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_SELECTED_ROUTE_ID, null)

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
                is Result.Success -> {
                    _routesState.value = RoutesUiState.Success(result.value)
                }
                is Result.Error -> {
                    _routesState.value = RoutesUiState.Error(result.failure.toString())
                }
            }
        }
    }

    /**
     * Llamar cuando el usuario selecciona una ruta.
     * - Persiste en SavedStateHandle
     * - Resetea búsqueda
     * - Carga clientes iniciales (LIMIT 50) vía repo.
     */
    fun onRouteSelected(routeId: String) {
        val current = selectedRouteId.value
        if (current == routeId) return

        savedStateHandle[KEY_SELECTED_ROUTE_ID] = routeId
        resetSearch()
        loadInitialData(routeId)
    }

    /** Compat: permite preseleccionar route desde args antiguos */
    fun ensureRouteSelected(routeId: String) {
        if (selectedRouteId.value.isNullOrBlank()) {
            onRouteSelected(routeId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        // si no hay ruta, ignorar para evitar queries accidentales
        if (selectedRouteId.value.isNullOrBlank()) {
            _searchQuery.value = query
            return
        }
        _searchQuery.value = query
    }

    fun resetSearch() {
        _searchQuery.value = ""
    }

    private fun loadInitialData(routeId: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SelectClientUiState.Loading
            when (val result = clientRepository.searchClientsByRoute(routeId, "")) {
                is Result.Success -> {
                    if (result.value.isEmpty()) {
                        _uiState.value = SelectClientUiState.Empty
                    } else {
                        _uiState.value = SelectClientUiState.Success(result.value.map { it.toUiModel() })
                    }
                }
                is Result.Error -> {
                    _uiState.value = SelectClientUiState.Error
                }
            }
        }
    }

    private fun performSearch(routeId: String, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SelectClientUiState.Loading
            when (val result = clientRepository.searchClientsByRoute(routeId, query)) {
                is Result.Success -> {
                    if (result.value.isEmpty()) {
                        _uiState.value = SelectClientUiState.Empty
                    } else {
                        _uiState.value = SelectClientUiState.Success(result.value.map { it.toUiModel() })
                    }
                }
                is Result.Error -> {
                    _uiState.value = SelectClientUiState.Error
                }
            }
        }
    }
}
