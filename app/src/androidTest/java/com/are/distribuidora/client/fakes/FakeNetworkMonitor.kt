package com.are.distribuidora.client.fakes

import com.are.distribuidora.core.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNetworkMonitor(initialState: Boolean = false) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialState)
    override val isOnline: Flow<Boolean> = _isOnline.asStateFlow()

    override fun isOnline(): Boolean = _isOnline.value

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
