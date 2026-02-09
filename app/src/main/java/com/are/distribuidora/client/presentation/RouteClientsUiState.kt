package com.are.distribuidora.client.presentation

import com.are.distribuidora.client.presentation.model.ClientUiModel

sealed class RouteClientsUiState {
    data object Loading : RouteClientsUiState()
    data class Success(val clients: List<ClientUiModel>) : RouteClientsUiState()
    data object Empty : RouteClientsUiState()
    data class Error(val message: String) : RouteClientsUiState()
}
