package com.usbanywhere.client.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.usbanywhere.data.RemoteServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class NsdDiscoveryHelper(
    private val context: Context,
    private val onServerDiscovered: (RemoteServer) -> Unit
) {
    companion object {
        private const val TAG = "NsdDiscoveryHelper"
        const val SERVICE_TYPE_USBANYWHERE = "_usbanywhere._tcp."
        const val SERVICE_TYPE_USBIP = "_usbip._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val isDiscovering = AtomicBoolean(false)
    private val resolveChannel = Channel<NsdServiceInfo>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Serialized consumer queue for resolveService calls to strictly prevent Android FAILURE_ALREADY_ACTIVE
        scope.launch {
            for (serviceInfo in resolveChannel) {
                resolveSingleService(serviceInfo)
            }
        }
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (nsdManager == null || isDiscovering.getAndSet(true)) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Service discovery started for: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${service.serviceName} (${service.serviceType})")
                resolveChannel.trySend(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS Discovery stopped: $serviceType")
                isDiscovering.set(false)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS Start discovery failed: $errorCode")
                isDiscovering.set(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS Stop discovery failed: $errorCode")
                isDiscovering.set(false)
            }
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE_USBANYWHERE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start mDNS discovery: ${e.message}")
            isDiscovering.set(false)
        }
    }

    private suspend fun resolveSingleService(service: NsdServiceInfo) {
        withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val resolver = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        Log.w(TAG, "mDNS Resolve failed for ${service.serviceName}: code $errorCode")
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Unit))
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                        val resolved = serviceInfo
                        if (resolved != null) {
                            val host = resolved.host?.hostAddress
                            val port = resolved.port
                            val name = resolved.serviceName ?: "USBAnywhere Host"

                            if (host != null && port > 0) {
                                Log.i(TAG, "mDNS Resolved server: $name at $host:$port")
                                val server = RemoteServer(
                                    ipAddress = host,
                                    port = port,
                                    hostName = name,
                                    isConnected = false
                                )
                                onServerDiscovered(server)
                            }
                        }
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Unit))
                        }
                    }
                }

                try {
                    nsdManager?.resolveService(service, resolver)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception calling resolveService: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(Unit))
                    }
                }
            }
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering.getAndSet(false)) return
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Exception stopping mDNS discovery: ${e.message}")
        }
        discoveryListener = null
    }
}
