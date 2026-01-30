package com.are.distribuidora.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.are.distribuidora.core.network.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context,
    ): NetworkMonitor = AndroidNetworkMonitor(context)

    private class AndroidNetworkMonitor(
        private val context: Context,
    ) : NetworkMonitor {
        override val isOnline: Flow<Boolean> = callbackFlow {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                trySend(false)
                close()
                return@callbackFlow
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                private val networks = mutableSetOf<android.net.Network>()

                override fun onAvailable(network: android.net.Network) {
                    networks += network
                    trySend(true)
                }

                override fun onLost(network: android.net.Network) {
                    networks -= network
                    trySend(networks.isNotEmpty())
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    if (hasInternet) {
                        networks += network
                        trySend(true)
                    } else { // Should check if other networks are valid? Stick to simple available/lost for now + rudimentary capability check if needed, but available usually implies connected.
                       // Simplified logic: If we have at least one valid network, we are online.
                       // callbackFlow emits whenever onAvailable/onLost happens.
                       // For robustness, simply sending 'true' on available and checking count on lost is decent.
                       // Better: check isOnline() status.
                       trySend(isOnline())
                    }
                }
            }

            val request = android.net.NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, callback)

            // Emit initial state
            trySend(isOnline())

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.conflate()

        override fun isOnline(): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
