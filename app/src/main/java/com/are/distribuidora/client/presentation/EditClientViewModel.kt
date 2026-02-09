package com.are.distribuidora.client.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.usecase.GetClientByIdUseCase
import com.are.distribuidora.client.domain.usecase.UpdateClientUseCase
import com.are.distribuidora.client.domain.util.TextFormatter
import com.are.distribuidora.client.presentation.util.LocationProvider
import com.are.distribuidora.client.presentation.util.LocationResult
import com.are.distribuidora.core.result.Result
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
class EditClientViewModel @Inject constructor(
    private val getClientByIdUseCase: GetClientByIdUseCase,
    private val updateClientUseCase: UpdateClientUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditClientUiState>(EditClientUiState.Loading)
    val uiState: StateFlow<EditClientUiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // Location State
    private val _locationState = MutableStateFlow<LocationState>(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private var currentClient: Client? = null

    fun load(clientId: String) {
        viewModelScope.launch {
            _uiState.value = EditClientUiState.Loading
            when (val result = getClientByIdUseCase(clientId)) {
                is Result.Success -> {
                    val client = result.value
                    if (client != null) {
                        currentClient = client
                        _uiState.value = EditClientUiState.Success(client)
                        // Pre-fill location
                        _locationState.value = LocationState(
                            latitude = client.latitude,
                            longitude = client.longitude
                        )
                    } else {
                        _uiState.value = EditClientUiState.Error("Cliente no encontrado")
                    }
                }
                is Result.Error -> {
                    _uiState.value = EditClientUiState.Error("Error al cargar cliente")
                }
            }
        }
    }

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
        name: String,
        phone: String?,
        address: String?,
        maxOrderAmountInCents: Long?,
        isActive: Boolean,
        routeId: String // Permitir editar ruta si la UI lo soporta, o mantener la actual
    ) {
        if (_isSaving.value) return

        val client = currentClient ?: return

        // 1. Validaciones básicas en VM (feedback inmediato)
        if (name.isBlank()) {
            sendEvent(Event.Error("El nombre es obligatorio"))
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            // 2. Crear objeto de dominio actualizado (manteniendo ID inmutable)
            val updatedClient = client.copy(
                name = TextFormatter.capitalizeWords(name.trim()),
                phone = TextFormatter.formatPhoneNumber(phone),
                address = address?.trim()?.let { TextFormatter.capitalizeWords(it) },
                maxOrderAmountInCents = maxOrderAmountInCents,
                isActive = isActive,
                routeId = routeId,
                latitude = _locationState.value.latitude,
                longitude = _locationState.value.longitude
            )

            // 3. Llamar al UseCase
            when (val result = updateClientUseCase(updatedClient)) {
                is Result.Success -> {
                    sendEvent(Event.Success)
                }
                is Result.Error -> {
                    val msg = (result.failure as? com.are.distribuidora.core.result.Failure.ValidationError)?.message 
                        ?: "Error al actualizar cliente"
                    sendEvent(Event.Error(msg))
                }
            }
            _isSaving.value = false
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    data class LocationState(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val isCapturing: Boolean = false,
        val error: String? = null
    )

    sealed class EditClientUiState {
        object Loading : EditClientUiState()
        data class Success(val client: Client) : EditClientUiState()
        data class Error(val message: String) : EditClientUiState()
    }

    sealed class Event {
        object Success : Event()
        data class Error(val message: String) : Event()
    }
}
