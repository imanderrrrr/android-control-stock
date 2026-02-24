package com.are.distribuidora.route.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SelectRouteUiState {
    data object Loading : SelectRouteUiState
    data object Empty : SelectRouteUiState
    data object Error : SelectRouteUiState
    data class Success(val routes: List<Route>) : SelectRouteUiState
}

@HiltViewModel
class SelectRouteViewModel @Inject constructor(
    private val getRoutesUseCase: GetRoutesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SelectRouteUiState>(SelectRouteUiState.Loading)
    val uiState: StateFlow<SelectRouteUiState> = _uiState.asStateFlow()

    init {
        loadRoutes()
    }

    fun loadRoutes() {
        viewModelScope.launch {
            _uiState.value = SelectRouteUiState.Loading
            getRoutesUseCase()
                .onSuccess { routes ->
                    if (routes.isEmpty()) {
                        _uiState.value = SelectRouteUiState.Empty
                    } else {
                        _uiState.value = SelectRouteUiState.Success(routes)
                    }
                }
                .onError {
                    _uiState.value = SelectRouteUiState.Error
                }
        }
    }
}
