package com.usbanywhere.gaming.sunshine

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SunshineHandoffTest {

    private var server: SunshineHandoffServer? = null
    private val testPort = 13242

    @Before
    fun setUp() {
        SunshineHandoffManager.setMode(HandoffMode.LOCAL_ANDROID_TV)
        server = SunshineHandoffServer(port = testPort)
        server?.start()
        Thread.sleep(100) // Allow server socket to bind
    }

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    @Test
    fun testSunshineHttpHandoffLifecycle() {
        // 1. Initial mode should be LOCAL_ANDROID_TV
        assertEquals(HandoffMode.LOCAL_ANDROID_TV, SunshineHandoffManager.currentMode.value)

        // 2. Query /api/status
        val statusRes = httpGet("http://127.0.0.1:$testPort/api/status")
        assertTrue(statusRes.contains("\"mode\":\"LOCAL_ANDROID_TV\""))

        // 3. Sunshine DO command hook: POST /api/stream/start
        val startRes = httpPost("http://127.0.0.1:$testPort/api/stream/start")
        assertTrue(startRes.contains("\"status\":\"ok\""))
        assertTrue(startRes.contains("STREAMING_PC"))
        assertEquals(HandoffMode.STREAMING_PC, SunshineHandoffManager.currentMode.value)

        // 4. Sunshine UNDO command hook: POST /api/stream/stop
        val stopRes = httpPost("http://127.0.0.1:$testPort/api/stream/stop")
        assertTrue(stopRes.contains("\"status\":\"ok\""))
        assertTrue(stopRes.contains("LOCAL_ANDROID_TV"))
        assertEquals(HandoffMode.LOCAL_ANDROID_TV, SunshineHandoffManager.currentMode.value)

        // 5. Toggle endpoint: POST /api/toggle
        val toggleRes = httpPost("http://127.0.0.1:$testPort/api/toggle")
        assertTrue(toggleRes.contains("STREAMING_PC"))
        assertEquals(HandoffMode.STREAMING_PC, SunshineHandoffManager.currentMode.value)
    }

    @Test
    fun testQuickToggleDetectorLogic() {
        var triggered = false
        val detector = QuickToggleDetector(holdDurationMs = 100L) {
            triggered = true
        }

        // Simulate Guide key down
        detector.processKey(KeyEvent.KEYCODE_BUTTON_MODE, isDown = true)
        assertFalse(triggered)

        // Simulate Select key down (Combo started)
        detector.processKey(KeyEvent.KEYCODE_BUTTON_SELECT, isDown = true)
        assertFalse(triggered)

        // Wait past threshold (100ms)
        Thread.sleep(150)

        // Another event keeps the combo alive and triggers
        val consumed = detector.processKey(KeyEvent.KEYCODE_BUTTON_SELECT, isDown = true)
        assertTrue(consumed)
        assertTrue(triggered)
    }

    @Test
    fun testSunshineHttpAuthTokenEnforcement() {
        val authPort = 13243
        SunshineHandoffManager.setMode(HandoffMode.LOCAL_ANDROID_TV)
        val authServer = SunshineHandoffServer(
            port = authPort,
            authTokenProvider = { "secret-lab-token" }
        )
        authServer.start()
        Thread.sleep(100)
        try {
            // 1. POST without token -> 401 and mode unchanged
            assertEquals(401, httpPostStatus("http://127.0.0.1:$authPort/api/stream/start"))
            assertEquals(HandoffMode.LOCAL_ANDROID_TV, SunshineHandoffManager.currentMode.value)

            // 2. POST with wrong X-USBAnywhere-Token -> 401
            assertEquals(401, httpPostStatus("http://127.0.0.1:$authPort/api/stream/start", mapOf("X-USBAnywhere-Token" to "wrong")))
            assertEquals(HandoffMode.LOCAL_ANDROID_TV, SunshineHandoffManager.currentMode.value)

            // 3. POST with valid X-USBAnywhere-Token -> 200 and STREAMING_PC
            assertEquals(200, httpPostStatus("http://127.0.0.1:$authPort/api/stream/start", mapOf("X-USBAnywhere-Token" to "secret-lab-token")))
            assertEquals(HandoffMode.STREAMING_PC, SunshineHandoffManager.currentMode.value)

            // 4. POST with valid Authorization Bearer -> 200 and LOCAL_ANDROID_TV
            assertEquals(200, httpPostStatus("http://127.0.0.1:$authPort/api/stream/stop", mapOf("Authorization" to "Bearer secret-lab-token")))
            assertEquals(HandoffMode.LOCAL_ANDROID_TV, SunshineHandoffManager.currentMode.value)

            // 5. GET /api/status remains accessible
            val statusRes = httpGet("http://127.0.0.1:$authPort/api/status")
            assertTrue(statusRes.contains("\"status\":\"ok\""))
        } finally {
            authServer.stop()
        }
    }

    private fun httpPostStatus(urlString: String, headers: Map<String, String> = emptyMap()): Int {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.doOutput = true
        conn.outputStream.write("{}".toByteArray())
        return conn.responseCode
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun httpPost(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        conn.doOutput = true
        conn.outputStream.write("{}".toByteArray())
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
