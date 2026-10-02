package com.example.ip_camera.server

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * High-performance JPEG encoder for MJPEG network streaming.
 * Compresses frame Bitmaps into standard JPEG byte arrays.
 */
class MjpegEncoder(
    var jpegQuality: Int = 80
) {
    private val outputStream = ByteArrayOutputStream(64 * 1024)

    /**
     * Compresses the given [bitmap] to a JPEG byte array.
     */
    @Synchronized
    fun encodeToJpeg(bitmap: Bitmap): ByteArray {
        outputStream.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(10, 100), outputStream)
        return outputStream.toByteArray()
    }
}
