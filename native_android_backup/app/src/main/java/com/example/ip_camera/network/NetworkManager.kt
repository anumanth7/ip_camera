package com.example.ip_camera.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Manages Wi-Fi network state detection and local IPv4 address resolution.
 * Detects network changes and updates stream URL information dynamically.
 */
class NetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "NetworkManager"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _ipAddress = MutableStateFlow<String?>(null)
    val ipAddress: StateFlow<String?> = _ipAddress.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        refreshNetworkInfo()
        registerNetworkCallback()
    }

    /**
     * Scans active network interfaces and link properties to find the local Wi-Fi IPv4 address.
     * Rejects 127.0.0.1 loopback addresses.
     */
    fun refreshNetworkInfo() {
        val detectedIp = findLocalIPv4Address()
        _ipAddress.value = detectedIp
        _isWifiConnected.value = isWifiCurrentlyConnected()
        Log.i(TAG, "Network updated: IP=$detectedIp, WiFiConnected=${_isWifiConnected.value}")
    }

    private fun isWifiCurrentlyConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Resolves the device's local IPv4 address on the Wi-Fi/LAN interface.
     */
    fun findLocalIPv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<String, String>>()

            for (netInterface in interfaces) {
                if (!netInterface.isUp || netInterface.isLoopback) continue

                val addresses = netInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: continue
                        if (!ip.startsWith("127.")) {
                            candidates.add(netInterface.name to ip)
                        }
                    }
                }
            }

            // Prioritize wlan0 / Wi-Fi interfaces
            val wifiCandidate = candidates.firstOrNull { it.first.contains("wlan", ignoreCase = true) }
            if (wifiCandidate != null) {
                return wifiCandidate.second
            }

            // Fallback to private LAN addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
            val privateLan = candidates.firstOrNull {
                it.second.startsWith("192.168.") ||
                        it.second.startsWith("10.") ||
                        it.second.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))
            }
            if (privateLan != null) {
                return privateLan.second
            }

            // Return first non-loopback candidate if any
            return candidates.firstOrNull()?.second
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving IP address: ${e.message}", e)
            return null
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val builder = NetworkRequest.Builder()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshNetworkInfo()
            }

            override fun onLost(network: Network) {
                refreshNetworkInfo()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                refreshNetworkInfo()
            }
        }
        networkCallback = callback

        try {
            cm.registerNetworkCallback(builder.build(), callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    fun release() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
    }
}
