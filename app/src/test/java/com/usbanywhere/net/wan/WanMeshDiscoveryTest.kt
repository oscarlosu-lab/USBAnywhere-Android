package com.usbanywhere.net.wan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WanMeshDiscoveryTest {

    @Test
    fun testTailscaleIpRangeDetection() {
        // Valid CGNAT / Tailscale IPs (100.64.0.0/10)
        assertTrue(WanInterfaceDetector.isTailscaleIp("100.64.0.1"))
        assertTrue(WanInterfaceDetector.isTailscaleIp("100.85.120.45"))
        assertTrue(WanInterfaceDetector.isTailscaleIp("100.127.255.254"))

        // Invalid IPs (Standard LAN, Public, Out of range)
        assertFalse(WanInterfaceDetector.isTailscaleIp("192.168.1.100"))
        assertFalse(WanInterfaceDetector.isTailscaleIp("10.0.0.1"))
        assertFalse(WanInterfaceDetector.isTailscaleIp("100.63.255.255"))
        assertFalse(WanInterfaceDetector.isTailscaleIp("100.128.0.1"))
        assertFalse(WanInterfaceDetector.isTailscaleIp("invalid_ip"))
    }

    @Test
    fun testPairingTokenSerializationAndParsing() {
        val original = WanPairingToken.PairingData(
            host = "100.88.99.12",
            port = 3240,
            serverName = "Shield-Pro-LivingRoom",
            isTlsEnabled = true
        )

        // 1. URI encoding & parsing
        val uri = original.toUri()
        assertTrue(uri.startsWith("usbip://100.88.99.12:3240?name=Shield-Pro-LivingRoom"))
        val parsedUri = WanPairingToken.parse(uri)
        assertNotNull(parsedUri)
        assertEquals(original.host, parsedUri?.host)
        assertEquals(original.port, parsedUri?.port)
        assertEquals(original.serverName, parsedUri?.serverName)
        assertEquals(original.isTlsEnabled, parsedUri?.isTlsEnabled)

        // 2. Compact Token encoding & parsing
        val token = original.toCompactToken()
        assertTrue(token.startsWith("UA1:"))
        val parsedToken = WanPairingToken.parse(token)
        assertNotNull(parsedToken)
        assertEquals(original.host, parsedToken?.host)
        assertEquals(original.port, parsedToken?.port)
        assertEquals(original.serverName, parsedToken?.serverName)
        assertEquals(original.isTlsEnabled, parsedToken?.isTlsEnabled)
    }

    @Test
    fun testCyberQrMatrixGeneration() {
        val testData = "usbip://100.64.1.2:3240?name=Server"
        val matrix = CyberQrGenerator.generateMatrix(testData)

        assertEquals(CyberQrGenerator.MATRIX_SIZE, matrix.size)
        assertEquals(CyberQrGenerator.MATRIX_SIZE, matrix[0].size)

        // Check Top-Left Finder Pattern (outer border = true, inner = true)
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
        assertTrue(matrix[6][6])
        assertTrue(matrix[3][3]) // Center of finder
        assertFalse(matrix[1][1]) // White ring in finder

        // Check Top-Right Finder Pattern
        val tr = CyberQrGenerator.MATRIX_SIZE - 7
        assertTrue(matrix[0][tr])
        assertTrue(matrix[0][tr + 6])
    }
}
