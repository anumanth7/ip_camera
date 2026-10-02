package com.example.ip_camera.vision

import android.graphics.Bitmap

/**
 * Pseudo-Thermal Vision Processor:
 * Transforms frame luminance/intensity into a thermal-style false-color visualization.
 *
 * NOTE & TECHNICAL LIMITATION:
 * The smartphone's built-in camera contains a standard CMOS/RGB sensor and cannot measure
 * physical radiometric temperature. This mode applies an intensity Look-Up Table (LUT)
 * simulating thermal ironbow false-color imaging.
 * It is explicitly NOT a true temperature measurement.
 *
 * Pipeline:
 * RGB frame -> Luminance extraction -> Thermal Color LUT mapping -> False-color Bitmap
 */
class PseudoThermalProcessor : VisionProcessor {
    override val mode: VisionMode = VisionMode.THERMAL

    // 256-color thermal gradient Look-Up Table (Ironbow / Thermal Spectrum)
    // Progression: dark purple -> blue -> cyan -> green -> yellow -> orange -> red -> white
    private val thermalLut: IntArray = generateThermalLut()

    // Reusable buffers
    private var pixelBuffer: IntArray? = null
    private var outputBuffer: IntArray? = null
    private var outputBitmap: Bitmap? = null

    companion object {
        private data class ColorStop(val position: Float, val r: Int, val g: Int, val b: Int)

        private fun generateThermalLut(): IntArray {
            val lut = IntArray(256)
            val stops = listOf(
                ColorStop(0.00f, 10, 0, 30),      // Dark Violet / Black
                ColorStop(0.15f, 20, 0, 110),     // Indigo
                ColorStop(0.30f, 0, 60, 180),     // Deep Blue
                ColorStop(0.45f, 0, 160, 160),    // Cyan
                ColorStop(0.60f, 0, 190, 40),     // Green
                ColorStop(0.72f, 220, 200, 0),    // Yellow
                ColorStop(0.85f, 240, 90, 0),     // Orange
                ColorStop(0.95f, 220, 20, 20),    // Red
                ColorStop(1.00f, 255, 255, 255)   // White Hot
            )

            for (i in 0..255) {
                val t = i / 255.0f
                // Find segment
                var lower = stops[0]
                var upper = stops.last()
                for (s in 0 until stops.size - 1) {
                    if (t >= stops[s].position && t <= stops[s + 1].position) {
                        lower = stops[s]
                        upper = stops[s + 1]
                        break
                    }
                }
                val segRange = (upper.position - lower.position).coerceAtLeast(0.0001f)
                val segT = ((t - lower.position) / segRange).coerceIn(0.0f, 1.0f)

                val r = (lower.r + segT * (upper.r - lower.r)).toInt().coerceIn(0, 255)
                val g = (lower.g + segT * (upper.g - lower.g)).toInt().coerceIn(0, 255)
                val b = (lower.b + segT * (upper.b - lower.b)).toInt().coerceIn(0, 255)

                lut[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return lut
        }
    }

    override fun process(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val size = width * height

        var pixels = pixelBuffer
        var outPixels = outputBuffer
        var outBm = outputBitmap

        if (pixels == null || pixels.size != size || outPixels == null || outBm == null) {
            pixels = IntArray(size)
            pixelBuffer = pixels
            outPixels = IntArray(size)
            outputBuffer = outPixels
            outBm?.recycle()
            outBm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outputBitmap = outBm
        }

        val inBuf: IntArray = pixels
        val outBuf: IntArray = outPixels
        val targetBm: Bitmap = outBm

        input.getPixels(inBuf, 0, width, 0, 0, width, height)

        for (i in 0 until size) {
            val color = inBuf[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Extract luminance index [0..255]
            val lum = (77 * r + 150 * g + 29 * b) shr 8
            outBuf[i] = thermalLut[lum]
        }

        targetBm.setPixels(outBuf, 0, width, 0, 0, width, height)
        return targetBm.copy(Bitmap.Config.ARGB_8888, false)
    }

    override fun release() {
        pixelBuffer = null
        outputBuffer = null
        outputBitmap?.recycle()
        outputBitmap = null
    }
}
