package com.kamildex.smsrelay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(
    private val context: Context,
    private val onAvailable: () -> Unit,
    private val onLost: () -> Unit
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { onAvailable() }
        override fun onLost(network: Network) { onLost() }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try { cm.registerNetworkCallback(request, callback) } catch (e: Exception) {}
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) {}
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork ?: return false
            return cm.getNetworkCapabilities(net)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }
}
