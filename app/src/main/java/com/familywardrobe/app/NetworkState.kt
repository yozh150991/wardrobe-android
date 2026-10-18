package com.familywardrobe.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connectivity changes never sign out an already authenticated local session. */
class NetworkState(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(read())
    val online = _online.asStateFlow()
    private fun read(): Boolean = manager?.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    fun refresh() { _online.value = read() }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refresh() }
        override fun onLost(network: Network) { refresh() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refresh() }
    }
    init { manager?.registerDefaultNetworkCallback(callback) }
}
