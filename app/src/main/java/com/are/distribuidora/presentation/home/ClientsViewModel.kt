package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.CreateRouteUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val createRouteUseCase: CreateRouteUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
) : ViewModel() {

    sealed interface Event {
        data object RouteCreated : Event
        data class Error(val message: String) : Event
    }

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    init {
        loadRoutes()
    }

    fun loadRoutes() {
        viewModelScope.launch {
            getRoutesUseCase().onSuccess { fetchedRoutes ->
                _routes.value = fetchedRoutes
            }
        }
    }

    fun createRoute(name: String, deliveryDay: Int) {
        viewModelScope.launch {
            val newRoute = Route(
                id = UUID.randomUUID().toString(),
                name = name,
                deliveryDay = deliveryDay,
                clientsCount = 0,
                synced = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            createRouteUseCase(newRoute)
                .onSuccess { 
                    _events.send(Event.RouteCreated)
                    loadRoutes() 
                }
                .onError { _events.send(Event.Error("Error al crear ruta")) }
        }
    }
}
