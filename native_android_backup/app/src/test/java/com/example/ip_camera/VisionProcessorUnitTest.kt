package com.example.ip_camera

import com.example.ip_camera.vision.NormalVisionProcessor
import com.example.ip_camera.vision.PseudoThermalProcessor
import com.example.ip_camera.vision.RealThermalProcessor
import com.example.ip_camera.vision.VisionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests verifying vision mode requirements and processor contracts.
 */
class VisionProcessorUnitTest {

    @Test
    fun testVisionModeTitlesAndSubtitles() {
        // Verify Normal Vision configuration
        assertEquals("NORMAL VISION", VisionMode.NORMAL.title)
        assertEquals("Original RGB Camera Feed", VisionMode.NORMAL.subtitle)

        // Verify Night Vision configuration complies with software enhancement disclaimer
        assertEquals("NIGHT VISION", VisionMode.NIGHT.title)
        assertEquals("Software Enhanced", VisionMode.NIGHT.subtitle)

        // Verify Thermal Vision configuration complies with pseudo-thermal disclaimer
        assertEquals("PSEUDO-THERMAL", VisionMode.THERMAL.title)
        assertEquals("No Temperature Measurement", VisionMode.THERMAL.subtitle)
    }

    @Test
    fun testProcessorModeAssociation() {
        val normal = NormalVisionProcessor()
        assertEquals(VisionMode.NORMAL, normal.mode)

        val pseudo = PseudoThermalProcessor()
        assertEquals(VisionMode.THERMAL, pseudo.mode)

        val realThermal = RealThermalProcessor(pseudo)
        assertEquals(VisionMode.THERMAL, realThermal.mode)
        assertEquals("REAL THERMAL (HARDWARE)", realThermal.title)
        assertEquals("External Radiometric Sensor Required", realThermal.subtitle)
    }

    @Test
    fun testRealThermalHardwareDetection() {
        val realThermal = RealThermalProcessor()
        // Smartphone hardware should accurately report no physical thermal sensor
        assertFalse(realThermal.isHardwareAvailable())
    }
}
