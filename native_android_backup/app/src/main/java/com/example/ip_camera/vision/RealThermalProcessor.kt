package com.example.ip_camera.vision

import android.graphics.Bitmap

/**
 * Real Thermal Processor (Hardware Sensor Integration Architecture):
 * Designed for future plug-and-play integration with dedicated external USB-C/Lightning
 * radiometric thermal sensors (e.g., FLIR ONE, Seek Thermal, Topdon, or InfiRay).
 *
 * NOTE:
 * Smartphones do not have built-in physical thermal microbolometer sensors.
 * This class provides the extensible architectural bridge required by SIH architecture:
 * when a physical thermal sensor SDK is connected via USB-C OTG, this class will receive
 * 16-bit raw radiometric temperature arrays and render calibrated thermal visualizations.
 *
 * Under no circumstances does this class fabricate simulated numerical temperature values.
 */
class RealThermalProcessor(
    private val fallbackProcessor: PseudoThermalProcessor = PseudoThermalProcessor()
) : VisionProcessor {
    override val mode: VisionMode = VisionMode.THERMAL
    override val title: String = "REAL THERMAL (HARDWARE)"
    override val subtitle: String = "External Radiometric Sensor Required"

    /**
     * Check if a supported external thermal hardware peripheral is connected and initialized.
     */
    fun isHardwareAvailable(): Boolean {
        // In the base smartphone setup, no external microbolometer is detected.
        return false
    }

    override fun process(input: Bitmap): Bitmap {
        if (!isHardwareAvailable()) {
            // Gracefully fall back to pseudo-thermal false-color mapping
            return fallbackProcessor.process(input)
        }
        // Future implementation will decode USB OTG UVC / FLIR SDK raw frames
        return fallbackProcessor.process(input)
    }

    override fun release() {
        fallbackProcessor.release()
    }
}
