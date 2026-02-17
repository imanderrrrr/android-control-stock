package com.are.distribuidora.data.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.are.distribuidora.domain.core.ConnectivityChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidConnectivityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : ConnectivityChecker {

    override suspend fun isOnline(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
