package com.usbanywhere.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val apkUrl: String? = null,
    val apkName: String? = null,
    val checksumUrl: String? = null,
    val releaseNotes: String = "",
    val error: String? = null
)

object AndroidUpdateChecker {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/oscarlosu-lab/USBAnywhere-Android/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val USER_AGENT = "USBAnywhere-Android-Updater/1.0"

    fun isAllowedDownloadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.startsWith("https://github.com/") ||
            url.startsWith("https://objects.githubusercontent.com/") ||
            url.startsWith("https://release-assets.githubusercontent.com/")
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val curParts = parseVersionParts(current)
        val latParts = parseVersionParts(latest)
        if (curParts.isEmpty() || latParts.isEmpty()) return false
        val maxLen = maxOf(curParts.size, latParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val l = latParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parseVersionParts(ver: String): List<Int> {
        val cleaned = ver.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').mapNotNull { it.toIntOrNull() }
    }

    fun parseReleaseJson(jsonText: String, currentVersion: String): UpdateCheckResult {
        val tagName = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(jsonText)?.groupValues?.get(1)?.trim().orEmpty()
        val body = Regex(""""body"\s*:\s*"((?:\\.|[^"\\])*)"""").find(jsonText)?.groupValues?.get(1).orEmpty()
            .replace("\\n", "\n")

        var apkUrl: String? = null
        var apkName: String? = null
        var checksumUrl: String? = null

        val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
        val urlRegex = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+)"""")
        val names = nameRegex.findAll(jsonText).toList()
        val urls = urlRegex.findAll(jsonText).toList()

        for (urlMatch in urls) {
            val url = urlMatch.groupValues[1]
            if (!isAllowedDownloadUrl(url)) continue
            val fallbackName = url.substringAfterLast('/')
            val nearbyName = names.lastOrNull { it.range.first < urlMatch.range.first && (urlMatch.range.first - it.range.first) < 800 }
                ?.groupValues?.get(1) ?: fallbackName

            if (nearbyName.endsWith(".apk", ignoreCase = true)) {
                val isPreferred = nearbyName.contains("universal", ignoreCase = true) ||
                    nearbyName.contains("release", ignoreCase = true) ||
                    apkUrl == null
                if (isPreferred) {
                    apkUrl = url
                    apkName = nearbyName
                }
            } else if (nearbyName.equals("SHA256SUMS.txt", ignoreCase = true) ||
                nearbyName.endsWith(".sha256", ignoreCase = true)
            ) {
                if (checksumUrl == null || nearbyName.equals("SHA256SUMS.txt", ignoreCase = true)) {
                    checksumUrl = url
                }
            }
        }

        val newer = tagName.isNotEmpty() && isNewerVersion(currentVersion, tagName)
        return UpdateCheckResult(
            updateAvailable = newer && !apkUrl.isNullOrBlank(),
            currentVersion = currentVersion,
            latestVersion = tagName.removePrefix("v").removePrefix("V"),
            apkUrl = apkUrl,
            apkName = apkName,
            checksumUrl = checksumUrl,
            releaseNotes = body
        )
    }

    fun extractExpectedSha256(checksumText: String, assetFileName: String): String? {
        val lines = checksumText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val hexRegex = Regex("^[0-9a-fA-F]{64}$")
        if (lines.size == 1 && hexRegex.matches(lines[0])) {
            return lines[0].lowercase()
        }
        val pairRegex = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+)$""")
        val hashes = mutableListOf<Pair<String, String>>()
        for (line in lines) {
            val m = pairRegex.matchEntire(line) ?: continue
            hashes.add(m.groupValues[1].lowercase() to m.groupValues[2].trim())
        }
        val exact = hashes.firstOrNull { it.second.equals(assetFileName, ignoreCase = true) }
        if (exact != null) return exact.first
        if (hashes.size == 1) return hashes[0].first
        return null
    }

    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyDownloadedApk(apkFile: File, expectedHash: String): Boolean {
        if (!apkFile.exists()) return false
        val actual = computeSha256Hex(apkFile)
        val ok = MessageDigest.isEqual(
            actual.lowercase().toByteArray(Charsets.US_ASCII),
            expectedHash.trim().lowercase().toByteArray(Charsets.US_ASCII)
        )
        if (!ok) {
            try { apkFile.delete() } catch (_: Exception) {}
        }
        return ok
    }

    fun checkForUpdate(currentVersion: String): UpdateCheckResult {
        return try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return UpdateCheckResult(false, currentVersion, currentVersion, error = "HTTP $code")
            }
            val json = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseReleaseJson(json, currentVersion)
        } catch (e: Exception) {
            UpdateCheckResult(false, currentVersion, currentVersion, error = e.message ?: "Update check failed")
        }
    }

    fun downloadAndVerifyApk(
        context: Context,
        info: UpdateCheckResult,
        onProgress: (Int) -> Unit = {}
    ): Result<File> {
        val apkUrl = info.apkUrl
        val apkName = info.apkName ?: "usbanywhere-update.apk"
        val checksumUrl = info.checksumUrl
        if (!isAllowedDownloadUrl(apkUrl) || !isAllowedDownloadUrl(checksumUrl)) {
            return Result.failure(SecurityException("Missing verified HTTPS release or checksum asset"))
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetApk = File(updatesDir, apkName)
        return try {
            val sumConn = (URL(checksumUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val sumText = sumConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val expectedHash = extractExpectedSha256(sumText, apkName)
                ?: return Result.failure(SecurityException("No matching SHA-256 entry for $apkName"))

            val apkConn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val total = apkConn.contentLengthLong.coerceAtLeast(1L)
            var downloaded = 0L
            apkConn.inputStream.use { input ->
                FileOutputStream(targetApk).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        downloaded += read
                        onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }

            if (!verifyDownloadedApk(targetApk, expectedHash)) {
                return Result.failure(SecurityException("SHA-256 verification failed"))
            }
            Result.success(targetApk)
        } catch (e: Exception) {
            try { targetApk.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    fun launchApkInstaller(context: Context, verifiedApk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", verifiedApk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
