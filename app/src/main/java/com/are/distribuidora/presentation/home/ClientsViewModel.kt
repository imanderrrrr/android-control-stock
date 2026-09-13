package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.CreateRouteUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel de la pestaña "Clientes": rutas + acceso a Cuentas por cobrar.
 *
 * El módulo de Cuentas por cobrar vive ahora en su pantalla dedicada
 * (com.are.distribuidora.pendingaccount.presentation). Aquí solo se expone el
 * conteo activo para el badge de la tarjeta de acceso.
 */
@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val createRouteUseCase: CreateRouteUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val pendingAccountDao: PendingAccountDao,
) : ViewModel() {

    sealed interface Event {
        data object RouteCreated : Event
        data class Error(val message: String) : Event
    }

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    /** Número de cuentas por cobrar activas (badge en la tarjeta de acceso). */
    val pendingCount: StateFlow<Int> =
        pendingAccountDao.observeActiveCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

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
                updatedAt = System.currentTimeMillis(),
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
