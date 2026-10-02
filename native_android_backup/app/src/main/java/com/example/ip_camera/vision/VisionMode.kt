package com.example.ip_camera.vision

/**
 * Supported vision processing modes for the IP camera module.
 */
enum class VisionMode(
    val title: String,
    val subtitle: String
) {
    NORMAL(
        title = "NORMAL VISION",
        subtitle = "Original RGB Camera Feed"
    ),
    NIGHT(
        title = "NIGHT VISION",
        subtitle = "Software Enhanced"
    ),
    THERMAL(
        title = "PSEUDO-THERMAL",
        subtitle = "No Temperature Measurement"
    )
}
