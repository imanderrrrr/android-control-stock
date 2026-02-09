package com.are.distribuidora.client.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.model.CreateClientInput
import com.are.distribuidora.client.domain.usecase.CreateClientUseCase
import com.are.distribuidora.client.presentation.util.LocationProvider
import com.are.distribuidora.client.presentation.util.LocationResult
import com.are.distribuidora.client.domain.util.TextFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateClientViewModel @Inject constructor(
    private val createClientUseCase: CreateClientUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {

    sealed interface Event {
        data object Success : Event
        data class Error(val message: String) : Event
    }

    private val _uiState = MutableStateFlow<CreateClientUiState>(CreateClientUiState.Idle)
    val uiState: StateFlow<CreateClientUiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Location State
    private val _locationState = MutableStateFlow<LocationState>(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    fun takeLocation() {
        if (_locationState.value.isCapturing) return

        viewModelScope.launch {
            _locationState.update { it.copy(isCapturing = true, error = null) }
            
            when (val result = locationProvider.getCurrentLocation()) {
                is LocationResult.Success -> {
                    _locationState.update { 
                        it.copy(
                            isCapturing = false, 
                            latitude = result.latitude, 
                            longitude = result.longitude
                        ) 
                    }
                }
                is LocationResult.PermissionDenied -> {
                    _locationState.update { it.copy(isCapturing = false, error = "Permiso de ubicación denegado") }
                }
                is LocationResult.GpsDisabled -> {
                    _locationState.update { it.copy(isCapturing = false, error = "GPS desactivado") }
                }
                is LocationResult.Timeout -> {
                    _locationState.update { it.copy(isCapturing = false, error = "Tiempo de espera agotado") }
                }
                is LocationResult.Error -> {
                    _locationState.update { it.copy(isCapturing = false, error = result.message) }
                }
            }
        }
    }

    fun clearLocation() {
        _locationState.update { it.copy(latitude = null, longitude = null, error = null) }
    }

    fun save(
        routeId: String,
        name: String,
        phone: String?,
        address: String?,
        maxOrderAmountInCents: Long?,
    ) {
        viewModelScope.launch {
            _uiState.value = CreateClientUiState.Loading
            try {
                // Validación básica
                if (name.isBlank()) {
                    _events.send(Event.Error("El nombre es obligatorio"))
                    _uiState.value = CreateClientUiState.Idle
                    return@launch
                }

                val input = CreateClientInput(
                    name = TextFormatter.capitalizeWords(name.trim()),
                    phone = TextFormatter.formatPhoneNumber(phone),
                    address = address?.trim()?.let { TextFormatter.capitalizeWords(it) },
                    latitude = _locationState.value.latitude,
                    longitude = _locationState.value.longitude,
                    maxOrderAmountInCents = maxOrderAmountInCents,
                    routeId = routeId
                )
                
                // El use case no retorna Result, solo ejecuta y lanza excepciones si hay error
                createClientUseCase(input)

                // Si llegamos aquí, fue exitoso
                _events.send(Event.Success)
                _uiState.value = CreateClientUiState.Idle

            } catch (e: IllegalArgumentException) {
                // Errores de validación del use case
                _events.send(Event.Error(e.message ?: "Error de validación"))
                _uiState.value = CreateClientUiState.Idle
            } catch (e: IllegalStateException) {
                // Errores de estado (ej: usuario no logueado)
                _events.send(Event.Error(e.message ?: "Error de estado"))
                _uiState.value = CreateClientUiState.Idle
            } catch (e: Exception) {
                // Cualquier otro error
                _events.send(Event.Error(e.message ?: "Error al crear cliente"))
                _uiState.value = CreateClientUiState.Idle
            }
        }
    }

    data class LocationState(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val isCapturing: Boolean = false,
        val error: String? = null
    )

    sealed class CreateClientUiState {
        object Idle : CreateClientUiState()
        object Loading : CreateClientUiState()
    }
}
