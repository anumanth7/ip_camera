import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:image/image.dart' as img;
import '../server/mjpeg_server.dart';
import '../vision/night_processor.dart';
import '../vision/normal_processor.dart';
import '../vision/thermal_processor.dart';
import '../vision/vision_mode.dart';
import '../vision/vision_processor.dart';

class _FrameJob {
  final Uint8List yPlane;
  final int width;
  final int height;
  final int rowStride;
  final VisionMode mode;

  _FrameJob({
    required this.yPlane,
    required this.width,
    required this.height,
    required this.rowStride,
    required this.mode,
  });
}

Uint8List _processFrameInIsolate(_FrameJob job) {
  VisionProcessor proc;
  switch (job.mode) {
    case VisionMode.normal:
      proc = NormalVisionProcessor();
      break;
    case VisionMode.night:
      proc = NightVisionProcessor();
      break;
    case VisionMode.thermal:
      proc = PseudoThermalProcessor();
      break;
  }

  final image = proc.processYPlane(
    job.yPlane,
    job.width,
    job.height,
    job.rowStride,
  );
  return proc.encodeJpeg(image, quality: 70);
}

class StreamManager {
  static final StreamManager instance = StreamManager._();
  StreamManager._();

  final MjpegServer _server = MjpegServer();
  final NormalVisionProcessor _normalProc = NormalVisionProcessor();
  final NightVisionProcessor _nightProc = NightVisionProcessor();
  final PseudoThermalProcessor _thermalProc = PseudoThermalProcessor();

  final ValueNotifier<VisionMode> activeMode = ValueNotifier<VisionMode>(VisionMode.normal);
  final ValueNotifier<bool> isStreaming = ValueNotifier<bool>(false);
  final ValueNotifier<Uint8List?> latestFrame = ValueNotifier<Uint8List?>(null);
  final ValueNotifier<int> clientCount = ValueNotifier<int>(0);
  final ValueNotifier<double> currentFps = ValueNotifier<double>(0.0);
  final ValueNotifier<String> serverUrl = ValueNotifier<String>('http://localhost:8080');

  VisionProcessor get _currentProcessor {
    switch (activeMode.value) {
      case VisionMode.normal:
        return _normalProc;
      case VisionMode.night:
        return _nightProc;
      case VisionMode.thermal:
        return _thermalProc;
    }
  }

  int _lastFrameTimeMs = 0;
  static const int _minFrameIntervalMs = 70; // ~14 FPS max to balance responsiveness & performance
  int _frameCount = 0;
  int _fpsTimerStart = 0;
  bool _isProcessing = false;

  Future<void> startStream({int port = 8080}) async {
    if (isStreaming.value) return;
    final success = await _server.start(
      port: port,
      getActiveModeName: () => activeMode.value.displayName,
    );
    if (success) {
      isStreaming.value = true;
      serverUrl.value = _server.serverUrl ?? 'http://localhost:$port';
      clientCount.value = _server.clientCount;
    }
  }

  Future<void> stopStream() async {
    if (!isStreaming.value) return;
    await _server.stop();
    isStreaming.value = false;
    clientCount.value = 0;
    currentFps.value = 0.0;
  }

  void setVisionMode(VisionMode newMode) {
    if (activeMode.value != newMode) {
      activeMode.value = newMode;
    }
  }

  /// Ingests and processes a camera frame (synthetic or RGB)
  void onFrameAvailable(img.Image rawFrame) {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastFrameTimeMs < _minFrameIntervalMs) {
      return; // Throttle
    }
    if (_isProcessing) {
      return;
    }

    _lastFrameTimeMs = now;
    _isProcessing = true;

    _frameCount++;
    if (_fpsTimerStart == 0) _fpsTimerStart = now;
    if (now - _fpsTimerStart >= 1000) {
      currentFps.value = (_frameCount * 1000.0) / (now - _fpsTimerStart);
      _frameCount = 0;
      _fpsTimerStart = now;
      clientCount.value = _server.clientCount;
    }

    try {
      final proc = _currentProcessor;
      final processed = proc.process(rawFrame);
      final jpegBytes = proc.encodeJpeg(processed, quality: 70);

      latestFrame.value = jpegBytes;

      if (isStreaming.value) {
        _server.broadcastFrame(jpegBytes);
      }
    } catch (_) {
    } finally {
      _isProcessing = false;
    }
  }

  /// Ingests raw YUV Y-plane on background isolate for maximum performance
  void onYPlaneAvailable(Uint8List yPlane, int width, int height, int rowStride) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - _lastFrameTimeMs < _minFrameIntervalMs) {
      return;
    }
    if (_isProcessing) return;

    _lastFrameTimeMs = now;
    _isProcessing = true;

    final mode = activeMode.value;
    try {
      final jpegBytes = await compute(
        _processFrameInIsolate,
        _FrameJob(
          yPlane: yPlane,
          width: width,
          height: height,
          rowStride: rowStride,
          mode: mode,
        ),
      );

      latestFrame.value = jpegBytes;

      if (isStreaming.value) {
        _server.broadcastFrame(jpegBytes);
      }

      _frameCount++;
      if (_fpsTimerStart == 0) _fpsTimerStart = now;
      if (now - _fpsTimerStart >= 1000) {
        currentFps.value = (_frameCount * 1000.0) / (now - _fpsTimerStart);
        _frameCount = 0;
        _fpsTimerStart = now;
        clientCount.value = _server.clientCount;
      }
    } catch (_) {
    } finally {
      _isProcessing = false;
    }
  }
}
