package com.are.distribuidora.presentation.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * Evento one-shot simple: cuando pasa a true, la UI navega a Home y luego llama consumeNavigation().
     */
    val navigateToHome: Boolean = false,
)
