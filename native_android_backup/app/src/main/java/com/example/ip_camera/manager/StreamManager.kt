package com.example.ip_camera.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.example.ip_camera.camera.CameraManager
import com.example.ip_camera.server.MjpegEncoder
import com.example.ip_camera.server.MjpegHttpServer
import com.example.ip_camera.vision.NightVisionProcessor
import com.example.ip_camera.vision.NormalVisionProcessor
import com.example.ip_camera.vision.PseudoThermalProcessor
import com.example.ip_camera.vision.VisionMode
import com.example.ip_camera.vision.VisionProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central orchestrator connecting CameraX, Vision Processors,
 * Local UI Preview, and the embedded MJPEG HTTP Server.
 */
class StreamManager(
    private val context: Context,
    val port: Int = 8080
) {
    companion object {
        private const val TAG = "StreamManager"
    }

    // Vision Processors
    private val normalProcessor = NormalVisionProcessor()
    private val nightProcessor = NightVisionProcessor()
    private val thermalProcessor = PseudoThermalProcessor()

    @Volatile
    private var activeProcessor: VisionProcessor = normalProcessor

    // Observable states
    private val _currentMode = MutableStateFlow(VisionMode.NORMAL)
    val currentMode: StateFlow<VisionMode> = _currentMode.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Server & Encoder
    private val mjpegEncoder = MjpegEncoder(jpegQuality = 80)
    private val httpServer = MjpegHttpServer(port = port) { _currentMode.value }

    // Camera Manager
    private val cameraManager = CameraManager(
        context = context,
        targetFps = 15,
        onFrameAvailable = { rawFrame ->
            handleNewFrame(rawFrame)
        },
        onError = { error ->
            _errorMessage.value = error
        }
    )

    /**
     * Changes the vision mode seamlessly without restarting the camera or HTTP server.
     */
    fun setVisionMode(mode: VisionMode) {
        if (_currentMode.value == mode) return

        activeProcessor = when (mode) {
            VisionMode.NORMAL -> normalProcessor
            VisionMode.NIGHT -> nightProcessor
            VisionMode.THERMAL -> thermalProcessor
        }
        _currentMode.value = mode
        Log.i(TAG, "Vision mode switched to: ${mode.title}")
    }

    /**
     * Starts the camera and the embedded HTTP server.
     */
    fun startStreaming(lifecycleOwner: LifecycleOwner) {
        if (_isStreaming.value) return

        _errorMessage.value = null
        try {
            // Start MJPEG HTTP server
            httpServer.start()
            // Start Camera
            cameraManager.startCamera(lifecycleOwner)
            _isStreaming.value = true
            Log.i(TAG, "Streaming started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
            _errorMessage.value = "Failed to start stream server: ${e.message}"
            stopStreaming()
        }
    }

    /**
     * Stops the camera and HTTP server, cleaning up resources.
     */
    fun stopStreaming() {
        if (!_isStreaming.value) return

        cameraManager.stopCamera()
        httpServer.stop()
        _previewBitmap.value = null
        _isStreaming.value = false
        Log.i(TAG, "Streaming stopped")
    }

    /**
     * Processes each incoming frame through the active vision mode pipeline
     * and dispatches to both the local UI preview and the network stream.
     */
    private fun handleNewFrame(rawFrame: Bitmap) {
        if (!_isStreaming.value) return

        try {
            // 1. Process frame with active Vision Processor
            val processedFrame = activeProcessor.process(rawFrame)

            // 2. Dispatch to local preview
            _previewBitmap.value = processedFrame

            // 3. Encode to JPEG and broadcast to network clients
            val jpegBytes = mjpegEncoder.encodeToJpeg(processedFrame)
            httpServer.broadcastFrame(jpegBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Error in frame processing pipeline: ${e.message}")
        }
    }

    fun release() {
        stopStreaming()
        normalProcessor.release()
        nightProcessor.release()
        thermalProcessor.release()
    }
}
