package com.are.distribuidora.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.core.network.NetworkMonitor
import com.are.distribuidora.route.domain.usecase.DownloadRoutesUseCase
import com.are.distribuidora.route.domain.usecase.GetRouteCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val downloadRoutesUseCase: DownloadRoutesUseCase,
    private val networkMonitor: NetworkMonitor,
    private val getRouteCountUseCase: GetRouteCountUseCase,
) : ViewModel() {

    private var hasSynced = false

    init {
        triggerSync()
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            // drop(1) to ignore initial state emission, we only want updates
            // distinctUntilChanged ensures we don't re-trigger on redundant true emissions
            // however, since we use conflate in implementation, we might still get updates.
            // Requirement: "Observar solo el evento OFFLINE → ONLINE"
            // And "NO ejecutar sync continuamente"
            var firstEmission = true
            networkMonitor.isOnline.collect { isOnline ->
                if (firstEmission) {
                    firstEmission = false
                    return@collect
                }
                if (isOnline) {
                    refreshRoutes()
                }
            }
        }
    }

    fun refreshRoutes() {
        viewModelScope.launch {
            kotlin.runCatching {
                downloadRoutesUseCase()
            }.also {
                // After attempt, log how many routes are in Room
                kotlin.runCatching { getRouteCountUseCase() }
            }
        }
    }

    private fun triggerSync() {
        if (hasSynced) return
        viewModelScope.launch {
            // Check connectivity if possible, though repository handles error silently mostly.
            // Requirement: Sync on Home entry.
            kotlin.runCatching {
                downloadRoutesUseCase()
                hasSynced = true
            }.also {
                kotlin.runCatching { getRouteCountUseCase() }
            }
        }
    }
}
