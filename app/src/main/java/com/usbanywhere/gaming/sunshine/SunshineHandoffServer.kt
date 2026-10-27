package com.usbanywhere.gaming.sunshine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Lightweight, zero-dependency embedded HTTP micro-server listening on port 3242.
 * Allows Sunshine (or custom scripts) to automatically handoff gamepads to the PC
 * when a stream starts, and restore local control when the stream stops.
 *
 * Endpoints:
 * - POST /api/stream/start -> Switch to STREAMING_PC mode (Sunshine Do Command)
 * - POST /api/stream/stop  -> Switch to LOCAL_ANDROID_TV mode (Sunshine Undo Command)
 * - POST /api/toggle       -> Toggle between LOCAL and STREAMING
 * - GET  /api/status       -> Current handoff status & attached gamepad details
 * - GET  /api/help         -> Sunshine command preparation configuration instructions
 */
class SunshineHandoffServer(
    private val port: Int = DEFAULT_PORT,
    private val onModeChanged: (HandoffMode) -> Unit = {}
) {

    companion object {
        const val DEFAULT_PORT = 3242
        private const val TAG = "SunshineHandoffServer"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("0.0.0.0", port))
            }
            isRunning = true
            try { Log.i(TAG, "Sunshine Handoff HTTP Server listening on port $port") } catch (e: Exception) {}

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleClient(clientSocket) }
                    } catch (e: SocketException) {
                        if (!isRunning) break
                        try { Log.w(TAG, "Server socket accept exception: ${e.message}") } catch (ign: Exception) {}
                    } catch (e: Exception) {
                        try { Log.e(TAG, "Unexpected error accepting connection", e) } catch (ign: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            try { Log.e(TAG, "Failed to bind Sunshine Handoff Server to port $port", e) } catch (ign: Exception) {}
            stop()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "Sunshine Handoff Server stopped")
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(output, 400, "Bad Request", "{\"error\":\"Invalid HTTP request\"}")
                return@withContext
            }

            val method = parts[0].uppercase()
            val path = parts[1].split("?")[0]

            // Drain headers
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
            }

            when {
                // Sunshine DO Command hook: /api/stream/start
                (method == "POST" || method == "GET") && path == "/api/stream/start" -> {
                    SunshineHandoffManager.setMode(HandoffMode.STREAMING_PC)
                    onModeChanged(HandoffMode.STREAMING_PC)
                    sendResponse(
                        output, 200, "OK",
                        "{\"status\":\"ok\",\"mode\":\"STREAMING_PC\",\"message\":\"Gamepad handed off to Sunshine PC streaming\"}"
                    )
                }

                // Sunshine UNDO Command hook: /api/stream/stop
                (method == "POST" || method == "GET") && path == "/api/stream/stop" -> {
                    SunshineHandoffManager.setMode(HandoffMode.LOCAL_ANDROID_TV)
                    onModeChanged(HandoffMode.LOCAL_ANDROID_TV)
                    sendResponse(
                        output, 200, "OK",
                        "{\"status\":\"ok\",\"mode\":\"LOCAL_ANDROID_TV\",\"message\":\"Gamepad restored to local Android TV navigation\"}"
                    )
                }

                // Quick Toggle endpoint
                (method == "POST" || method == "GET") && path == "/api/toggle" -> {
                    val newMode = SunshineHandoffManager.toggleMode()
                    onModeChanged(newMode)
                    sendResponse(
                        output, 200, "OK",
                        "{\"status\":\"ok\",\"mode\":\"${newMode.name}\",\"title\":\"${newMode.title}\"}"
                    )
                }

                // Status query
                method == "GET" && path == "/api/status" -> {
                    val currentMode = SunshineHandoffManager.currentMode.value
                    val quickToggle = "HOLD_GUIDE_SELECT_2.5S"
                    val json = "{\"status\":\"ok\",\"mode\":\"${currentMode.name}\",\"title\":\"${currentMode.title}\",\"quickToggleCombo\":\"$quickToggle\",\"port\":$port}"
                    sendResponse(output, 200, "OK", json)
                }

                // Help instructions for Sunshine Web UI
                method == "GET" && (path == "/api/help" || path == "/") -> {
                    val helpText = """
                        USBAnywhere Sunshine Handoff Server
                        ===================================
                        Port: $port
                        Current Mode: ${SunshineHandoffManager.currentMode.value.name}

                        SUNSHINE CONFIGURATION:
                        In Sunshine Web UI -> Configuration -> General -> Command Preparations:
                        - Do Command:   curl -s -X POST http://<ANDROID_IP>:$port/api/stream/start
                        - Undo Command: curl -s -X POST http://<ANDROID_IP>:$port/api/stream/stop

                        GAMEPAD QUICK-TOGGLE:
                        Hold Guide + Select (or Back + Start) for 2.5 seconds on your gamepad
                        to toggle between Local Android TV and PC Streaming instantly!
                    """.trimIndent()
                    sendTextResponse(output, 200, "OK", helpText)
                }

                else -> {
                    sendResponse(output, 404, "Not Found", "{\"error\":\"Endpoint not found\",\"path\":\"$path\"}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling HTTP request: ${e.message}")
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusText: String, jsonBody: String) {
        val bytes = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendTextResponse(output: OutputStream, statusCode: Int, statusText: String, textBody: String) {
        val bytes = textBody.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
