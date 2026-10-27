package com.usbanywhere.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.usbanywhere.MainActivity
import com.usbanywhere.data.ServerRuntimeStats
import com.usbanywhere.protocol.UsbIpConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class UsbIpServerService : Service() {

    companion object {
        private const val TAG = "UsbIpServerService"
        private const val CHANNEL_ID = "usbanywhere_server_channel"
        private const val NOTIFICATION_ID = 3240
        private const val DISCOVERY_PORT = 3241
        private const val PROBE_MESSAGE = "USBANYWHERE_DISCOVERY_PROBE"
        private const val NSD_SERVICE_TYPE = "_usbanywhere._tcp."

        const val ACTION_START = "com.usbanywhere.server.START"
        const val ACTION_STOP  = "com.usbanywhere.server.STOP"

        private val _serverStats = MutableStateFlow(ServerRuntimeStats())
        val serverStats: StateFlow<ServerRuntimeStats> = _serverStats.asStateFlow()

        var hostManagerInstance: UsbHostManager? = null
            private set

        fun startService(context: Context) {
            val intent = Intent(context, UsbIpServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, UsbIpServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var statsJob: Job? = null
    private var udpResponderJob: Job? = null
    private var udpResponderSocket: DatagramSocket? = null

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val activeWorkers = CopyOnWriteArrayList<UsbIpServerWorker>()
    private val totalPackets = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private var lastPacketCount = 0L
    private var lastStatsTimestamp = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (hostManagerInstance == null) {
            hostManagerInstance = UsbHostManager(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundNotification()
                acquireLocks()
                startServer()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USBAnywhere USB/IP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background USB/IP Server for exporting local USB OTG devices"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USBAnywhere Server Active")
            .setContentText("Listening on port ${UsbIpConstants.DEFAULT_PORT} (OTG USB/IP)")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBAnywhere::ServerWakeLock").apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours timeout
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "USBAnywhere::ServerWifiLock").apply {
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire locks: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (_: Exception) {}
    }

    private fun startServer() {
        if (serverSocket != null && !serverSocket!!.isClosed) return

        acceptJob = serviceScope.launch {
            try {
                val port = com.usbanywhere.data.AppPreferences.getServerPort(applicationContext)
                val socket = ServerSocket(port)
                serverSocket = socket
                val localIp = getLocalIpAddress()

                _serverStats.value = ServerRuntimeStats(
                    isRunning = true,
                    listeningIp = localIp,
                    listeningPort = port,
                    activeClientsCount = 0
                )
                Log.i(TAG, "USBAnywhere USB/IP Server started on $localIp:$port")

                // Start mDNS and UDP discovery responders in parallel
                registerNsdService(port)
                startUdpResponder()
                startStatsMonitor(localIp)

                while (isActive && !socket.isClosed) {
                    val clientSocket: Socket = socket.accept()
                    Log.i(TAG, "Accepted connection from ${clientSocket.inetAddress}")

                    val worker = UsbIpServerWorker(
                        socket = clientSocket,
                        hostManager = hostManagerInstance ?: UsbHostManager(applicationContext),
                        onPacketTransferred = { packets, bytes ->
                            totalPackets.addAndGet(packets)
                            totalBytes.addAndGet(bytes)
                        },
                        onClientDisconnected = { deadWorker ->
                            activeWorkers.remove(deadWorker)
                            updateClientCount()
                        }
                    )
                    activeWorkers.add(worker)
                    updateClientCount()
                    Thread(worker, "UsbIpWorker-${clientSocket.inetAddress}").start()
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server socket error: ${e.message}")
                    _serverStats.value = _serverStats.value.copy(
                        isRunning = false,
                        lastError = e.message
                    )
                }
            }
        }
    }

    private fun registerNsdService(port: Int) {
        try {
            nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
            val rawName = "USBAnywhere-${Build.MODEL.replace(" ", "-")}"
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = rawName
                serviceType = NSD_SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                    Log.i(TAG, "mDNS Service registered: ${registeredInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS Service registration failed: $errorCode")
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i(TAG, "mDNS Service unregistered successfully")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS Service unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Exception during mDNS registration: ${e.message}")
        }
    }

    private fun unregisterNsdService() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during mDNS unregistration: ${e.message}")
        }
        registrationListener = null
    }

    private fun startUdpResponder() {
        udpResponderJob?.cancel()
        udpResponderJob = serviceScope.launch {
            try {
                val socket = DatagramSocket(DISCOVERY_PORT)
                udpResponderSocket = socket
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                val serverName = "USBANYWHERE_SERVER:${Build.MODEL}"
                val responseBytes = serverName.toByteArray()

                while (isActive && !socket.isClosed) {
                    try {
                        socket.receive(packet)
                        val receivedStr = String(packet.data, 0, packet.length).trim()
                        if (receivedStr == PROBE_MESSAGE) {
                            val replyPacket = DatagramPacket(
                                responseBytes,
                                responseBytes.size,
                                packet.address,
                                packet.port
                            )
                            socket.send(replyPacket)
                            Log.d(TAG, "Replied to UDP discovery probe from ${packet.address}")
                        }
                    } catch (e: Exception) {
                        if (!isActive || socket.isClosed) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP Responder socket could not bind: ${e.message}")
            }
        }
    }

    private fun stopUdpResponder() {
        udpResponderJob?.cancel()
        try {
            udpResponderSocket?.close()
        } catch (_: Exception) {}
        udpResponderSocket = null
    }

    private fun startStatsMonitor(ip: String) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val currentPackets = totalPackets.get()
                val deltaPackets = currentPackets - lastPacketCount
                val deltaSec = (now - lastStatsTimestamp) / 1000.0
                val hz = if (deltaSec > 0) deltaPackets / deltaSec else 0.0

                lastPacketCount = currentPackets
                lastStatsTimestamp = now

                _serverStats.value = _serverStats.value.copy(
                    isRunning = true,
                    listeningIp = ip,
                    activeClientsCount = activeWorkers.size,
                    totalPacketsSent = currentPackets,
                    totalBytesSent = totalBytes.get(),
                    currentPollingRateHz = hz
                )
            }
        }
    }

    private fun updateClientCount() {
        _serverStats.value = _serverStats.value.copy(
            activeClientsCount = activeWorkers.size
        )
    }

    private fun stopServer() {
        unregisterNsdService()
        stopUdpResponder()
        acceptJob?.cancel()
        statsJob?.cancel()
        for (w in activeWorkers) {
            w.close()
        }
        activeWorkers.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        _serverStats.value = ServerRuntimeStats(
            isRunning = false,
            listeningIp = "0.0.0.0",
            activeClientsCount = 0
        )
        Log.i(TAG, "USBAnywhere USB/IP Server stopped")
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (_: Exception) {}
        return "0.0.0.0"
    }
}
