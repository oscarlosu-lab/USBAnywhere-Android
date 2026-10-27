package com.usbanywhere.net.wan

import java.security.MessageDigest

/**
 * Lightweight, zero-dependency Cyber QR matrix generator for pairing token visualization in Compose.
 * Generates standard QR finder patterns and deterministic matrix representation.
 */
object CyberQrGenerator {

    const val MATRIX_SIZE = 25

    fun generateMatrix(data: String): Array<BooleanArray> {
        val matrix = Array(MATRIX_SIZE) { BooleanArray(MATRIX_SIZE) }

        // 1. Draw 3 Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, MATRIX_SIZE - 7, 0)
        drawFinderPattern(matrix, 0, MATRIX_SIZE - 7)

        // 2. Draw Timing Patterns (Row 6 and Col 6)
        for (i in 8 until MATRIX_SIZE - 8) {
            val bit = (i % 2 == 0)
            matrix[6][i] = bit
            matrix[i][6] = bit
        }

        // 3. Populate Data Region using cryptographic hash of data string
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data.toByteArray(Charsets.UTF_8))

        var bitIndex = 0
        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                // Skip finder patterns and timing lines
                if (isReserved(r, c)) continue

                val byteVal = hash[bitIndex % hash.size].toInt()
                val bitVal = ((byteVal shr (bitIndex % 8)) and 1) == 1
                matrix[r][c] = bitVal
                bitIndex++
            }
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                matrix[startR + r][startC + c] = isOuter || isInner
            }
        }
    }

    private fun isReserved(r: Int, c: Int): Boolean {
        // Top-left finder + separator
        if (r <= 7 && c <= 7) return true
        // Top-right finder + separator
        if (r <= 7 && c >= MATRIX_SIZE - 8) return true
        // Bottom-left finder + separator
        if (r >= MATRIX_SIZE - 8 && c <= 7) return true
        // Timing lines
        if (r == 6 || c == 6) return true
        return false
    }
}
