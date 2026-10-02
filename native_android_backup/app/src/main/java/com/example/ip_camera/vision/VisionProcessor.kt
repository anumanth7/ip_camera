package com.example.ip_camera.vision

import android.graphics.Bitmap

/**
 * Common interface for all frame vision processors.
 * Implementations process raw camera frames according to the selected vision mode.
 */
interface VisionProcessor {
    /**
     * The vision mode supported by this processor.
     */
    val mode: VisionMode

    /**
     * UI display title (e.g. "NORMAL VISION", "NIGHT VISION", "PSEUDO-THERMAL").
     */
    val title: String get() = mode.title

    /**
     * Technical disclaimer / subtitle (e.g. "Software Enhanced", "No Temperature Measurement").
     */
    val subtitle: String get() = mode.subtitle

    /**
     * Process an incoming camera frame bitmap and return the processed bitmap.
     * Implementations can either return a new bitmap or update a reused bitmap.
     *
     * @param input Frame bitmap from camera (ARGB_8888).
     * @return Processed bitmap representing the vision mode output.
     */
    fun process(input: Bitmap): Bitmap

    /**
     * Release any reusable buffers, lookup tables, or allocated native memory.
     */
    fun release() {}
}
