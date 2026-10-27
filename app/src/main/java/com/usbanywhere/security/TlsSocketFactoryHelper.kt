package com.usbanywhere.security

import android.util.Log
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Provides TLS 1.3 / 1.2 encrypted socket factories for secure WAN / Internet USB/IP tunneling.
 */
object TlsSocketFactoryHelper {

    private const val TAG = "TlsSocketFactoryHelper"
    const val TLS_PORT = 3243

    private val permissiveTrustManager = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    )

    /**
     * Creates an SSLContext configured for peer-to-peer encrypted tunnels.
     */
    fun createTlsContext(): SSLContext {
        val protocols = listOf("TLSv1.3", "TLSv1.2", "TLS")
        for (proto in protocols) {
            try {
                val ctx = SSLContext.getInstance(proto)
                ctx.init(null, permissiveTrustManager, SecureRandom())
                return ctx
            } catch (e: Exception) {
                try { Log.w(TAG, "Protocol $proto not available, trying next...") } catch (ign: Exception) {}
            }
        }
        return SSLContext.getDefault()
    }

    fun getClientSocketFactory(): SSLSocketFactory {
        return createTlsContext().socketFactory
    }

    /**
     * Wraps a connected raw TCP socket into a TLS encrypted socket.
     */
    fun wrapSocketAsTlsClient(plainSocket: Socket, host: String, port: Int): SSLSocket {
        val factory = getClientSocketFactory()
        val sslSocket = factory.createSocket(plainSocket, host, port, true) as SSLSocket
        sslSocket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
        sslSocket.startHandshake()
        return sslSocket
    }
}
