package com.example.ip_camera.vision

import android.graphics.Bitmap

/**
 * Normal Vision Processor:
 * Passes the camera frame through with minimal modification.
 *
 * Pipeline:
 * Camera -> Normal RGB frame -> JPEG encoding -> MJPEG stream
 */
class NormalVisionProcessor : VisionProcessor {
    override val mode: VisionMode = VisionMode.NORMAL

    override fun process(input: Bitmap): Bitmap {
        // Return a copy so downstream encoders and preview consumers can use it safely
        return input.copy(input.config ?: Bitmap.Config.ARGB_8888, false)
    }

    override fun release() {
        // No resources to release
    }
}
