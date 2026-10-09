package com.jinki.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device currently has a working internet path.
 *
 * Plain language: the app watches the network and flips this flag the
 * moment the connection drops or comes back, so uploads can show "Waiting
 * for connection…" and resume instantly instead of polling on a timer.
 */
class ConnectivityObserver(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(manager.isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = manager.isCurrentlyOnline()
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    fun start() {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean =
    getNetworkCapabilities(activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
