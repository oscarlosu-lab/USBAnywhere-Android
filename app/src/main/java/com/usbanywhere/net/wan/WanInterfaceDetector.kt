package com.usbanywhere.net.wan

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Detects active WAN, Tailscale, and WireGuard mesh interfaces for zero-config remote play.
 */
object WanInterfaceDetector {

    private const val TAG = "WanInterfaceDetector"

    data class WanNetworkInfo(
        val isTailscaleActive: Boolean = false,
        val isWireGuardActive: Boolean = false,
        val isVpnActive: Boolean = false,
        val meshIp: String? = null,
        val interfaceName: String? = null,
        val networkTypeDesc: String = "Local LAN Only"
    )

    private val _networkInfo = MutableStateFlow(scanInterfaces())
    val networkInfo: StateFlow<WanNetworkInfo> = _networkInfo.asStateFlow()

    fun refresh(): WanNetworkInfo {
        val info = scanInterfaces()
        _networkInfo.value = info
        return info
    }

    private fun scanInterfaces(): WanNetworkInfo {
        var isTailscale = false
        var isWireGuard = false
        var isVpn = false
        var detectedIp: String? = null
        var ifaceName: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return WanNetworkInfo()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue

                val name = iface.name.lowercase()
                val isTun = name.startsWith("tun") || name.startsWith("tap")
                val isWg = name.startsWith("wg")
                val isTs = name.contains("tailscale")

                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue

                        // Check Tailscale CGNAT range: 100.64.0.0/10 (100.64.x.x to 100.127.x.x)
                        if (isTailscaleIp(hostAddress) || isTs) {
                            isTailscale = true
                            isVpn = true
                            detectedIp = hostAddress
                            ifaceName = iface.name
                            break
                        }

                        // Check WireGuard interface
                        if (isWg || (isTun && (hostAddress.startsWith("10.") || hostAddress.startsWith("172.")))) {
                            isWireGuard = true
                            isVpn = true
                            if (detectedIp == null) {
                                detectedIp = hostAddress
                                ifaceName = iface.name
                            }
                        }
                    }
                }
                if (isTailscale) break
            }
        } catch (e: Exception) {
            try { Log.w(TAG, "Error scanning network interfaces: ${e.message}") } catch (ign: Exception) {}
        }

        val desc = when {
            isTailscale -> "Tailscale Mesh Active ($detectedIp)"
            isWireGuard -> "WireGuard VPN Active ($detectedIp)"
            isVpn -> "VPN Tunnel Active ($detectedIp)"
            else -> "Local LAN / Wi-Fi Active"
        }

        return WanNetworkInfo(
            isTailscaleActive = isTailscale,
            isWireGuardActive = isWireGuard,
            isVpnActive = isVpn,
            meshIp = detectedIp,
            interfaceName = ifaceName,
            networkTypeDesc = desc
        )
    }

    /**
     * Checks if an IPv4 address falls within the IANA Shared Address Space (CGNAT / Tailscale): 100.64.0.0/10
     */
    fun isTailscaleIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && (second in 64..127)
    }
}
