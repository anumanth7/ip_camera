package com.example.ip_camera.server

import android.util.Log
import com.example.ip_camera.vision.VisionMode
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded HTTP server providing:
 * 1. GET / - Responsive Test Web Page displaying live stream & mode
 * 2. GET /stream - High-performance multipart/x-mixed-replace MJPEG stream
 * 3. GET /status - JSON endpoint with current server and mode status
 */
class MjpegHttpServer(
    val port: Int = 8080,
    private val getVisionMode: () -> VisionMode = { VisionMode.NORMAL }
) {
    companion object {
        private const val TAG = "MjpegHttpServer"
        private const val BOUNDARY = "--frame"
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var serverExecutor: ExecutorService? = null
    private val clientStreams = ConcurrentHashMap<ClientHandler, Boolean>()

    val activeClientCount: Int get() = clientStreams.size

    /**
     * Starts the HTTP server on the specified port.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return

        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            serverSocket = socket
            isRunning.set(true)

            val executor = Executors.newCachedThreadPool()
            serverExecutor = executor

            executor.execute {
                Log.i(TAG, "MJPEG Server started on port $port")
                while (isRunning.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        executor.execute {
                            handleClient(client)
                        }
                    } catch (e: SocketException) {
                        if (isRunning.get()) {
                            Log.w(TAG, "Server socket exception: ${e.message}")
                        }
                        break
                    } catch (e: IOException) {
                        Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MJPEG HTTP server on port $port", e)
            stop()
            throw e
        }
    }

    /**
     * Broadcasts a new JPEG frame to all connected MJPEG clients.
     * Drops frames per-client if the client's network connection is slow to maintain lowest latency.
     */
    fun broadcastFrame(jpegBytes: ByteArray) {
        if (!isRunning.get() || clientStreams.isEmpty()) return

        for (client in clientStreams.keys) {
            client.offerFrame(jpegBytes)
        }
    }

    /**
     * Handles an incoming HTTP request.
     */
    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 15000 // 15s timeout for initial request

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "HTTP Request: $requestLine from ${socket.remoteSocketAddress}")

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendNotFound(socket.getOutputStream())
                socket.close()
                return
            }

            val path = parts[1].split("?")[0]

            when {
                path == "/stream" -> {
                    // Reset timeout for continuous streaming
                    socket.soTimeout = 0
                    handleStreamClient(socket)
                }
                path == "/" || path == "/index.html" -> {
                    serveTestPage(socket)
                }
                path == "/status" -> {
                    serveStatusJson(socket)
                }
                else -> {
                    sendNotFound(socket.getOutputStream())
                    socket.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client: ${e.message}")
            try {
                socket.close()
            } catch (_: IOException) {}
        }
    }

    /**
     * Handles /stream requests with multipart/x-mixed-replace MJPEG.
     */
    private fun handleStreamClient(socket: Socket) {
        val out = socket.getOutputStream()
        val writer = BufferedWriter(OutputStreamWriter(out))

        // Send MJPEG HTTP headers
        val header = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Server: SIH-IP-Camera/1.0\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n\r\n"

        writer.write(header)
        writer.flush()

        val handler = ClientHandler(socket, out)
        clientStreams[handler] = true
        Log.i(TAG, "New MJPEG client connected: ${socket.remoteSocketAddress}. Total clients: ${clientStreams.size}")

        try {
            handler.runLoop()
        } finally {
            clientStreams.remove(handler)
            handler.close()
            Log.i(TAG, "MJPEG client disconnected: ${socket.remoteSocketAddress}. Remaining: ${clientStreams.size}")
        }
    }

    /**
     * Serves the HTML test page at /.
     */
    private fun serveTestPage(socket: Socket) {
        val currentMode = getVisionMode()
        val html = buildTestPageHtml(currentMode)
        val bytes = html.toByteArray(Charsets.UTF_8)

        val out = socket.getOutputStream()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"

        out.write(response.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
        socket.close()
    }

    /**
     * Serves JSON status at /status.
     */
    private fun serveStatusJson(socket: Socket) {
        val mode = getVisionMode()
        val json = """{"status":"streaming","mode":"${mode.name}","title":"${mode.title}","clients":$activeClientCount}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val out = socket.getOutputStream()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"

        out.write(response.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
        socket.close()
    }

    private fun sendNotFound(out: OutputStream) {
        val body = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n$body"
        out.write(response.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Builds the standalone test webpage matching user specifications.
     */
    private fun buildTestPageHtml(initialMode: VisionMode): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SIH Camera Stream - Test Monitor</title>
    <style>
        :root {
            --bg-color: #0d1117;
            --card-bg: #161b22;
            --border-color: #30363d;
            --accent-green: #2ea043;
            --accent-blue: #58a6ff;
            --accent-amber: #d29922;
            --text-main: #f0f6fc;
            --text-sub: #8b949e;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            margin: 0;
            padding: 24px 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        .container {
            max-width: 800px;
            width: 100%;
        }
        header {
            text-align: center;
            margin-bottom: 20px;
        }
        h1 {
            margin: 0 0 8px 0;
            font-size: 1.8rem;
            letter-spacing: 1px;
            color: var(--text-main);
        }
        .subheading {
            color: var(--text-sub);
            font-size: 0.95rem;
        }
        .video-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 8px 24px rgba(0,0,0,0.5);
            margin-bottom: 20px;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 380px;
            position: relative;
        }
        .stream-img {
            max-width: 100%;
            height: auto;
            display: block;
            object-fit: contain;
        }
        .badge-overlay {
            position: absolute;
            top: 12px;
            left: 12px;
            background: rgba(0,0,0,0.75);
            backdrop-filter: blur(4px);
            padding: 6px 12px;
            border-radius: 6px;
            font-size: 0.85rem;
            font-weight: 600;
            letter-spacing: 0.5px;
            border: 1px solid rgba(255,255,255,0.1);
        }
        .info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }
        .info-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 16px;
        }
        .info-label {
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.8px;
            color: var(--text-sub);
            margin-bottom: 6px;
        }
        .info-val {
            font-size: 1.1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .dot-live {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background-color: var(--accent-green);
            box-shadow: 0 0 8px var(--accent-green);
            animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.85); }
            100% { opacity: 1; transform: scale(1); }
        }
        .url-box {
            background-color: #0b0f14;
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 10px 12px;
            font-family: monospace;
            font-size: 0.95rem;
            color: var(--accent-blue);
            word-break: break-all;
            user-select: all;
        }
        .disclaimer-card {
            background-color: rgba(210, 153, 34, 0.1);
            border: 1px solid rgba(210, 153, 34, 0.3);
            border-radius: 8px;
            padding: 14px;
            font-size: 0.85rem;
            color: #e3b341;
            line-height: 1.4;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>SIH Camera Stream</h1>
            <div class="subheading">Problem Statement 26034 &bull; Camera & Vision Streaming Module</div>
        </header>

        <div class="video-card">
            <div class="badge-overlay" id="streamBadge">MODE: ${initialMode.title}</div>
            <img class="stream-img" src="/stream" alt="Live MJPEG Camera Stream" onerror="this.alt='Stream disconnected. Tap reconnect.';" />
        </div>

        <div class="info-grid">
            <div class="info-card">
                <div class="info-label">Connection</div>
                <div class="info-val"><span class="dot-live"></span> <span id="connStatus">CONNECTED</span></div>
            </div>
            <div class="info-card">
                <div class="info-label">Current Vision Mode</div>
                <div class="info-val" id="modeTitle" style="color: var(--accent-blue);">${initialMode.title}</div>
            </div>
            <div class="info-card">
                <div class="info-label">Stream Port</div>
                <div class="info-val">$port</div>
            </div>
        </div>

        <div class="info-card" style="margin-bottom: 20px;">
            <div class="info-label">Stream URL (Direct MJPEG Source)</div>
            <div class="url-box" id="streamUrl">http://${'$'}{location.host}/stream</div>
        </div>

        <div class="disclaimer-card">
            <strong>Technical Note:</strong> Night Vision mode uses software-enhanced RGB imagery. Thermal view mode uses false-color pseudo-thermal LUT visualization and does not measure physical temperature.
        </div>
    </div>

    <script>
        // Poll status every 1.5 seconds to reflect mode changes without stream interruption
        async function checkStatus() {
            try {
                const res = await fetch('/status');
                if (res.ok) {
                    const data = await res.json();
                    document.getElementById('connStatus').innerText = 'CONNECTED';
                    document.getElementById('modeTitle').innerText = data.title || data.mode;
                    document.getElementById('streamBadge').innerText = 'MODE: ' + (data.title || data.mode);
                }
            } catch (err) {
                document.getElementById('connStatus').innerText = 'DISCONNECTED';
            }
        }
        setInterval(checkStatus, 1500);
    </script>
</body>
</html>
"""
    }

    /**
     * Stops the HTTP server and disconnects all clients.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return

        Log.i(TAG, "Stopping MJPEG server...")
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null

        // Disconnect all clients
        for (client in clientStreams.keys) {
            client.close()
        }
        clientStreams.clear()

        serverExecutor?.shutdownNow()
        serverExecutor = null
        Log.i(TAG, "MJPEG server stopped.")
    }

    /**
     * Inner handler for each active streaming client socket.
     */
    private inner class ClientHandler(
        private val socket: Socket,
        private val outputStream: OutputStream
    ) {
        // Queue holds 1 frame max. New frames overwrite slow/old frames to keep latency minimum.
        private val frameQueue = ArrayBlockingQueue<ByteArray>(2)
        @Volatile
        private var isOpen = true

        fun offerFrame(jpegBytes: ByteArray) {
            if (!isOpen) return
            // If full, drop oldest frame to maintain real-time low latency
            if (frameQueue.remainingCapacity() == 0) {
                frameQueue.poll()
            }
            frameQueue.offer(jpegBytes)
        }

        fun runLoop() {
            try {
                while (isOpen && isRunning.get() && !socket.isClosed) {
                    val frame = frameQueue.poll(500, TimeUnit.MILLISECONDS) ?: continue

                    // Build MJPEG frame header
                    val frameHeader = "$BOUNDARY\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n\r\n"

                    outputStream.write(frameHeader.toByteArray(Charsets.US_ASCII))
                    outputStream.write(frame)
                    outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))
                    outputStream.flush()
                }
            } catch (e: Exception) {
                // Client closed connection (Broken pipe, Socket closed, etc.)
            } finally {
                close()
            }
        }

        fun close() {
            isOpen = false
            try {
                socket.close()
            } catch (_: IOException) {}
        }
    }
}
