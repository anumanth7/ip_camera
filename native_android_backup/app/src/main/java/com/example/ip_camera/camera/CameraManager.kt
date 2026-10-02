package com.example.ip_camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, rear camera binding, frame extraction,
 * and rate-limited frame delivery to the vision stream pipeline.
 */
class CameraManager(
    private val context: Context,
    private val targetFps: Int = 15,
    private val onFrameAvailable: (Bitmap) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "CameraManager"
        private val TARGET_RESOLUTION = Size(640, 480)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null

    private val minFrameIntervalMs = 1000L / targetFps.coerceIn(5, 30)
    private var lastFrameTimestampMs = 0L

    @Volatile
    private var isCameraActive = false

    /**
     * Initializes CameraX and binds the rear camera to the given [LifecycleOwner].
     */
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        if (isCameraActive) return

        val executor = Executors.newSingleThreadExecutor()
        cameraExecutor = executor

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Unbind previous bindings
                provider.unbindAll()

                // Target rear camera by default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Configure ImageAnalysis for optimal streaming resolution and low latency
                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(TARGET_RESOLUTION)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                imageAnalysis = analysis

                // Bind to lifecycle
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    analysis
                )

                isCameraActive = true
                Log.i(TAG, "CameraX successfully bound to lifecycle with rear camera")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases: ${e.message}", e)
                onError("Camera Error: ${e.localizedMessage ?: "Camera unavailable"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Processes each ImageProxy from CameraX, rotates to upright, throttles FPS,
     * and forwards the Bitmap to the vision processing pipeline.
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameTimestampMs < minFrameIntervalMs) {
                // Drop frame to maintain 10-15 target FPS and keep CPU cool
                return
            }
            lastFrameTimestampMs = now

            val rawBitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            val uprightBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            onFrameAvailable(uprightBitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Unbinds camera use cases and shuts down background executor.
     */
    fun stopCamera() {
        isCameraActive = false
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            cameraExecutor?.shutdownNow()
            cameraExecutor = null
            Log.i(TAG, "Camera stopped and resources cleaned up")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping camera: ${e.message}")
        }
    }
}
