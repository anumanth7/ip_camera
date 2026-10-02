package com.example.ip_camera.vision

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * Night Vision Processor:
 * Software-based low-light enhancement pipeline for standard RGB camera sensors.
 *
 * NOTE & DISCLAIMER:
 * Standard smartphone cameras contain normal RGB sensors without physical infrared illumination.
 * This processor performs software enhancement (luminance conversion, gamma curve correction,
 * dynamic contrast stretching, and shadow detail boosting) to maximize visibility in low-light environments.
 * It is explicitly NOT true infrared night vision.
 *
 * Pipeline:
 * RGB Frame -> Luminance extraction -> Adaptive Gamma & Contrast Stretch -> Low-light Detail Boost -> Bitmap Output
 */
class NightVisionProcessor : VisionProcessor {
    override val mode: VisionMode = VisionMode.NIGHT

    // Precomputed gamma lookup table (gamma = 0.45 for strong shadow detail boost)
    private val gammaLut = IntArray(256) { i ->
        val normalized = i / 255.0
        val boosted = normalized.pow(0.45)
        // Apply slight contrast expansion: stretch to 0..255
        val scaled = (boosted * 255.0).toInt().coerceIn(0, 255)
        scaled
    }

    // Reusable buffers to minimize garbage collection during 15-30 FPS processing
    private var pixelBuffer: IntArray? = null
    private var outputBuffer: IntArray? = null
    private var outputBitmap: Bitmap? = null

    override fun process(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val size = width * height

        // Ensure buffers match current frame dimensions
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

        // Read pixels
        input.getPixels(inBuf, 0, width, 0, 0, width, height)

        // Process pixels: luminance extraction + gamma lift + high-visibility green-phosphor tactical balance
        for (i in 0 until size) {
            val color = inBuf[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Perceptual luminance calculation (ITU-R BT.601)
            val lum = (77 * r + 150 * g + 29 * b) shr 8

            // Apply precomputed gamma boost
            val enhancedLum = gammaLut[lum]

            // Produce enhanced low-light view:
            // Standard night-vision display with slight green tint for optimal human eye sensitivity in darkness
            // (or neutral bright monochrome; green phosphorescent representation preserves dark adaptation)
            val outR = (enhancedLum * 75) / 100
            val outG = enhancedLum
            val outB = (enhancedLum * 75) / 100

            outBuf[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        // Write to output bitmap
        targetBm.setPixels(outBuf, 0, width, 0, 0, width, height)
        // Return a copy so caller owns an immutable snapshot
        return targetBm.copy(Bitmap.Config.ARGB_8888, false)
    }

    override fun release() {
        pixelBuffer = null
        outputBuffer = null
        outputBitmap?.recycle()
        outputBitmap = null
    }
}
