package com.usbanywhere.net.wan

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Encodes and decodes WAN pairing tokens and QR code payloads for 1-click zero-config setup.
 */
object WanPairingToken {

    data class PairingData(
        val host: String,
        val port: Int = 3240,
        val serverName: String = "USBAnywhere-Server",
        val isTlsEnabled: Boolean = false,
        val tlsPort: Int = 3243,
        val pairingCode: String = "",
        val tlsFingerprint: String = ""
    ) {
        fun toUri(): String {
            return "usbip://$host:$port?name=$serverName&tls=$isTlsEnabled"
        }

        fun toCompactToken(): String {
            val raw = "${host}|${port}|${serverName}|${if (isTlsEnabled) 1 else 0}"
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
            return "UA1:$encoded"
        }

        fun toUa2Token(): String {
            val raw = "$host|$port|$serverName|$tlsPort|$pairingCode|$tlsFingerprint"
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
            return "UA2:$encoded"
        }
    }

    /**
     * Parses a URI (usbip://...), compact token (UA1:...), or pinned pairing token (UA2:...).
     */
    fun parse(input: String): PairingData? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 0. Check UA2 token: UA2:<base64url(host|port|name|tlsPort|code|fingerprint)>
        if (trimmed.startsWith("UA2:")) {
            try {
                val b64 = trimmed.substring(4)
                val rawBytes = Base64.getUrlDecoder().decode(b64)
                val raw = String(rawBytes, StandardCharsets.UTF_8)
                val parts = raw.split("|")
                if (parts.size >= 5) {
                    val host = parts[0].trim()
                    if (host.isEmpty()) return null
                    val port = parts[1].toIntOrNull() ?: 3240
                    val name = parts[2].trim().ifEmpty { "USBAnywhere-Server" }
                    val tlsPort = parts[3].toIntOrNull() ?: 3243
                    val code = parts[4].trim()
                    val fp = parts.getOrNull(5)?.trim()?.lowercase() ?: ""
                    return PairingData(
                        host = host,
                        port = port,
                        serverName = name,
                        isTlsEnabled = tlsPort > 0,
                        tlsPort = tlsPort,
                        pairingCode = code,
                        tlsFingerprint = fp
                    )
                }
            } catch (_: Exception) {
                return null
            }
        }

        // 1. Check compact token: UA1:<base64>
        if (trimmed.startsWith("UA1:")) {
            try {
                val b64 = trimmed.substring(4)
                val rawBytes = Base64.getUrlDecoder().decode(b64)
                val raw = String(rawBytes, StandardCharsets.UTF_8)
                val parts = raw.split("|")
                if (parts.size >= 3) {
                    val host = parts[0]
                    val port = parts[1].toIntOrNull() ?: 3240
                    val name = parts[2]
                    val tls = parts.getOrNull(3) == "1"
                    return PairingData(host, port, name, tls)
                }
            } catch (e: Exception) {
                return null
            }
        }

        // 2. Check standard URI: usbip://<host>:<port>?name=<name>&tls=<true|false>
        if (trimmed.startsWith("usbip://")) {
            try {
                val withoutScheme = trimmed.substring(8)
                val hostPortAndQuery = withoutScheme.split("?")
                val hostPort = hostPortAndQuery[0].split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 3240 else 3240

                var name = "USBAnywhere-Server"
                var tls = false

                if (hostPortAndQuery.size > 1) {
                    val params = hostPortAndQuery[1].split("&")
                    for (param in params) {
                        val kv = param.split("=")
                        if (kv.size == 2) {
                            when (kv[0].lowercase()) {
                                "name" -> name = kv[1]
                                "tls" -> tls = kv[1].toBoolean()
                            }
                        }
                    }
                }
                return PairingData(host, port, name, tls)
            } catch (e: Exception) {
                return null
            }
        }

        return null
    }
}
