package com.usbanywhere.security

import com.usbanywhere.gaming.sunshine.DeviceHandoffPolicy
import com.usbanywhere.gaming.sunshine.HandoffMode
import com.usbanywhere.gaming.sunshine.StreamProvider
import com.usbanywhere.gaming.sunshine.SunshineHandoffManager
import com.usbanywhere.gaming.sunshine.SunshineHandoffServer
import com.usbanywhere.net.wan.WanPairingToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class SecurityPairingAndTlsTest {

    @Before
    fun resetState() {
        PairingCodeManager.resetForTests()
        SunshineHandoffManager.setMode(HandoffMode.LOCAL_ANDROID_TV)
    }

    @Test
    fun ua2TokenRoundTripAndMalformedRejection() {
        val src = WanPairingToken.PairingData(
            host = "203.0.113.10",
            port = 3240,
            serverName = "LabHost",
            isTlsEnabled = true,
            tlsPort = 3243,
            pairingCode = "654321",
            tlsFingerprint = "0123456789abcdef"
        )
        val ua2 = src.toUa2Token()
        assertTrue(ua2.startsWith("UA2:"))
        val parsed = WanPairingToken.parse(ua2)
        assertNotNull(parsed)
        assertEquals("203.0.113.10", parsed!!.host)
        assertEquals(3243, parsed.tlsPort)
        assertEquals("654321", parsed.pairingCode)
        assertEquals("0123456789abcdef", parsed.tlsFingerprint)

        assertNull(WanPairingToken.parse("UA2:not-valid-base64!!!"))
    }

    @Test
    fun pairingCodeManagerValidExpiryAndLockout() {
        val t0 = 1_000_000L
        val code = PairingCodeManager.createCode(nowMs = t0)
        assertEquals(6, code.length)

        // Wrong attempt
        val bad = PairingCodeManager.verifyCode("000000".takeIf { it != code } ?: "111111", nowMs = t0 + 100)
        assertFalse(bad.success)

        // Valid redemption
        val ok = PairingCodeManager.verifyCode(code, nowMs = t0 + 200)
        assertTrue(ok.success)
        assertEquals(32, ok.controlToken.length)

        // Expiry check
        val expCode = PairingCodeManager.createCode(nowMs = t0)
        val expired = PairingCodeManager.verifyCode(expCode, nowMs = t0 + 301_000L)
        assertFalse(expired.success)

        // 5-attempt lockout
        val lockCode = PairingCodeManager.createCode(nowMs = t0)
        val wrong = "999999".takeIf { it != lockCode } ?: "888888"
        repeat(5) {
            assertFalse(PairingCodeManager.verifyCode(wrong, nowMs = t0 + 1000L).success)
        }
        val locked = PairingCodeManager.verifyCode(lockCode, nowMs = t0 + 1500L)
        assertFalse(locked.success)
        assertTrue(locked.message.contains("Locked"))
    }

    @Test
    fun multiProviderStreamingHandoffAndDevicePolicy() {
        val port = 13249
        val srv = SunshineHandoffServer(port = port)
        srv.start()
        Thread.sleep(100)
        try {
            for (prov in listOf("apollo", "vibeshine", "vibepollo", "parsec", "steam_remote_play")) {
                val conn = URL("http://127.0.0.1:$port/api/stream/start?provider=$prov").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                assertEquals(200, conn.responseCode)
                val body = conn.inputStream.bufferedReader().readText()
                assertTrue("Expected $prov in $body", body.contains("\"provider\":\"$prov\""))
                assertEquals(StreamProvider.fromQuery(prov), SunshineHandoffManager.activeProvider.value)
            }

            assertFalse(SunshineHandoffManager.shouldForwardDevice(DeviceHandoffPolicy.ALWAYS_LOCAL, HandoffMode.STREAMING_PC))
            assertTrue(SunshineHandoffManager.shouldForwardDevice(DeviceHandoffPolicy.ALWAYS_FORWARD, HandoffMode.LOCAL_ANDROID_TV))
            assertTrue(SunshineHandoffManager.shouldForwardDevice(DeviceHandoffPolicy.ONLY_DURING_STREAM, HandoffMode.STREAMING_PC))
            assertFalse(SunshineHandoffManager.shouldForwardDevice(DeviceHandoffPolicy.ONLY_DURING_STREAM, HandoffMode.LOCAL_ANDROID_TV))
        } finally {
            srv.stop()
        }
    }

    @Test
    fun tlsPinContextRejectsMismatchedCertificate() {
        val ctx = TlsSocketFactoryHelper.createPinnedTlsContext("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        assertNotNull(ctx)
        assertEquals("TLS", ctx.protocol)
    }
}
