package com.usbanywhere.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AndroidUpdateCheckerTest {

    @Test
    fun semverComparisonWorksCorrectly() {
        assertTrue(AndroidUpdateChecker.isNewerVersion("3.4.1", "v3.5.0"))
        assertTrue(AndroidUpdateChecker.isNewerVersion("3.9.9", "3.10.0"))
        assertFalse(AndroidUpdateChecker.isNewerVersion("3.4.1", "v3.4.1"))
        assertFalse(AndroidUpdateChecker.isNewerVersion("3.5.0", "3.4.1"))
    }

    @Test
    fun parseReleaseJsonExtractsApkAndSha256Sums() {
        val json = """
        {
          "tag_name": "v3.5.0",
          "body": "Release notes",
          "assets": [
            {
              "name": "USBAnywhere-v3.5.0-universal.apk",
              "browser_download_url": "https://github.com/oscarlosu-lab/USBAnywhere-Android/releases/download/v3.5.0/USBAnywhere-v3.5.0-universal.apk"
            },
            {
              "name": "SHA256SUMS.txt",
              "browser_download_url": "https://github.com/oscarlosu-lab/USBAnywhere-Android/releases/download/v3.5.0/SHA256SUMS.txt"
            }
          ]
        }
        """.trimIndent()
        val res = AndroidUpdateChecker.parseReleaseJson(json, "3.4.1")
        assertTrue(res.updateAvailable)
        assertEquals("3.5.0", res.latestVersion)
        assertEquals("USBAnywhere-v3.5.0-universal.apk", res.apkName)
        assertNotNull(res.checksumUrl)
    }

    @Test
    fun extractAndVerifySha256DeletesTamperedFile() {
        val tmpDir = Files.createTempDirectory("usbany_upd_test").toFile()
        try {
            val apk = File(tmpDir, "app.apk").apply { writeBytes("hello-apk".toByteArray()) }
            val validHash = AndroidUpdateChecker.computeSha256Hex(apk)
            val sums = "$validHash  app.apk\n0000000000000000000000000000000000000000000000000000000000000000  other.apk"

            assertEquals(validHash, AndroidUpdateChecker.extractExpectedSha256(sums, "app.apk"))
            assertTrue(AndroidUpdateChecker.verifyDownloadedApk(apk, validHash))
            assertTrue(apk.exists())

            apk.writeBytes("tampered-apk".toByteArray())
            assertFalse(AndroidUpdateChecker.verifyDownloadedApk(apk, validHash))
            assertFalse("Tampered APK must be deleted", apk.exists())
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
