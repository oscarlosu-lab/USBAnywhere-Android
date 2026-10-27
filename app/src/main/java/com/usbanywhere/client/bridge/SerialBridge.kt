package com.usbanywhere.client.bridge

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class SerialBridge(
    private val bridgePort: Int = 8888
) {
    companion object {
        private const val TAG = "SerialBridge"
    }

    private var serverSocket: ServerSocket? = null
    private var bridgeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning = false
        private set

    fun start(
        remoteIn: InputStream,
        remoteOut: OutputStream,
        onClientConnected: () -> Unit = {}
    ) {
        if (isRunning) return
        isRunning = true

        bridgeJob = scope.launch {
            try {
                val server = ServerSocket(bridgePort)
                serverSocket = server
                Log.i(TAG, "Serial Bridge listening on 127.0.0.1:$bridgePort")

                while (isActive && !server.isClosed) {
                    val localClient = server.accept()
                    Log.i(TAG, "App connected to serial bridge on port $bridgePort")
                    onClientConnected()

                    val localIn = localClient.getInputStream()
                    val localOut = localClient.getOutputStream()

                    // Forward Local -> Remote
                    val localToRemote = launch {
                        val buf = ByteArray(1024)
                        while (isActive && !localClient.isClosed) {
                            val read = localIn.read(buf)
                            if (read < 0) break
                            remoteOut.write(buf, 0, read)
                            remoteOut.flush()
                        }
                    }

                    // Forward Remote -> Local
                    val remoteToLocal = launch {
                        val buf = ByteArray(1024)
                        while (isActive && !localClient.isClosed) {
                            val read = remoteIn.read(buf)
                            if (read < 0) break
                            localOut.write(buf, 0, read)
                            localOut.flush()
                        }
                    }

                    localToRemote.join()
                    remoteToLocal.join()
                    try { localClient.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "Serial bridge stopped: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        bridgeJob?.cancel()
        bridgeJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
