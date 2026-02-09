package com.are.distribuidora.client.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.usecase.DeleteClientUseCase
import com.are.distribuidora.client.domain.usecase.ObserveClientsByRouteUseCase
import com.are.distribuidora.client.presentation.mapper.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class RouteClientsViewModel @Inject constructor(
    private val observeClientsByRouteUseCase: ObserveClientsByRouteUseCase,
    private val deleteClientUseCase: DeleteClientUseCase,
) : ViewModel() {

    private val _routeId = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")

    private val _uiState = MutableStateFlow<RouteClientsUiState>(RouteClientsUiState.Loading)
    val uiState: StateFlow<RouteClientsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _routeId,
                searchQuery
                    .debounce(300)
                    .distinctUntilChanged(),
            ) { routeId, query ->
                Pair(routeId, query)
            }.collect { (routeId, query) ->
                if (routeId.isBlank()) {
                    _uiState.value = RouteClientsUiState.Loading
                    return@collect
                }

                observeClientsByRouteUseCase(routeId)
                    .map { clients ->
                        val trimmed = query.trim()
                        val filtered = if (trimmed.isEmpty()) {
                            clients
                        } else {
                            val q = trimmed.lowercase()
                            clients.filter { client ->
                                client.name.lowercase().contains(q) ||
                                    (client.phone?.contains(trimmed) == true) ||
                                    (client.address?.lowercase()?.contains(q) == true)
                            }
                        }

                        if (filtered.isEmpty()) {
                            RouteClientsUiState.Empty
                        } else {
                            // CRÍTICO: crear nueva lista con .toList() para forzar nueva referencia
                            RouteClientsUiState.Success(
                                clients = filtered.map { it.toUiModel() }.toList()
                            )
                        }
                    }
                    .collect { state ->
                        _uiState.value = state
                    }
            }
        }
    }

    fun load(routeId: String) {
        _routeId.value = routeId
    }

    fun deleteClient(id: String) {
        viewModelScope.launch {
            deleteClientUseCase(id)
        }
    }
}
