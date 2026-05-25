package com.are.distribuidora.presentation.login

import androidx.lifecycle.ViewModel
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.usecase.DownloadRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val downloadRoutesUseCase: DownloadRoutesUseCase,
) : ViewModel() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun submitLogin() {
        val current = _uiState.value
        val email = current.email.trim()
        val password = current.password

        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email y contraseña son obligatorios") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            when (val result = authRepository.login(email, password)) {
                is Result.Success -> {
                    // Importante: NO forzar logout si fallara algo luego.
                    _uiState.update { it.copy(isLoading = false, navigateToHome = true) }

                    // Lanzar la descarga de rutas en background, sin bloquear la UI.
                    launch {
                        kotlin.runCatching {
                            downloadRoutesUseCase()
                        }
                        // Errores silenciosos: ignoramos o podemos loguear si se quiere.
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.failure.toUiMessage(),
                        )
                    }
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    private fun Failure.toUiMessage(): String {
        return when (this) {
            Failure.NetworkError -> "No hay conexión. Intenta de nuevo."
            Failure.DatabaseError -> "Error local. Intenta de nuevo."
            Failure.NotFound -> "Usuario no encontrado."
            is Failure.ValidationError -> this.message.ifBlank { "Credenciales inválidas" }
            is Failure.DuplicateOrder -> "Error inesperado"
            is Failure.OrderLimitExceeded -> "Error inesperado"
            Failure.UnknownError -> "Error inesperado"
        }
    }
}
