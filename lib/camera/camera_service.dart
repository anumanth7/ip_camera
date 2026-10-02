import 'dart:async';
import 'dart:math' as math;
import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:image/image.dart' as img;
import '../manager/stream_manager.dart';

class CameraService {
  static final CameraService instance = CameraService._();
  CameraService._();

  CameraController? _controller;
  CameraController? get controller => _controller;

  final ValueNotifier<bool> isCameraInitialized = ValueNotifier<bool>(false);
  final ValueNotifier<bool> isSynthetic = ValueNotifier<bool>(false);
  final ValueNotifier<String> cameraError = ValueNotifier<String>('');

  Timer? _syntheticTimer;
  double _syntheticPhase = 0.0;

  Future<void> initialize() async {
    debugPrint('CameraService: initializing...');
    try {
      final cameras = await availableCameras();
      debugPrint('CameraService: found ${cameras.length} cameras');
      if (cameras.isEmpty) {
        _startSyntheticFeed('No physical camera detected. Running simulated feed.');
        return;
      }

      // Prioritize rear/back camera
      final camera = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );
      debugPrint('CameraService: selected camera ${camera.name} (${camera.lensDirection})');

      _controller = CameraController(
        camera,
        ResolutionPreset.low, // 320x240 or 352x288, optimal for low-latency streaming
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );

      await _controller!.initialize();
      debugPrint('CameraService: controller initialized successfully');
      isCameraInitialized.value = true;
      isSynthetic.value = false;

      await _controller!.startImageStream((CameraImage image) {
        _processCameraImage(image);
      });
      debugPrint('CameraService: image stream started');
    } catch (e, st) {
      debugPrint('CameraService error: $e\n$st');
      _startSyntheticFeed('Camera initialization: $e. Falling back to simulated feed.');
    }
  }

  void _processCameraImage(CameraImage image) {
    if (image.planes.isNotEmpty) {
      final plane = image.planes[0];
      StreamManager.instance.onYPlaneAvailable(
        plane.bytes,
        image.width,
        image.height,
        plane.bytesPerRow,
      );
    }
  }

  void _startSyntheticFeed(String reason) {
    cameraError.value = reason;
    isSynthetic.value = true;
    isCameraInitialized.value = true;

    _syntheticTimer?.cancel();
    _syntheticTimer = Timer.periodic(const Duration(milliseconds: 70), (_) {
      _generateSyntheticFrame();
    });
  }

  void _generateSyntheticFrame() {
    const width = 320;
    const height = 240;
    _syntheticPhase += 0.05;

    final image = img.Image(width: width, height: height, numChannels: 3);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        final cx = x - width / 2;
        final cy = y - height / 2;
        final dist = math.sqrt(cx * cx + cy * cy);
        final wave = (math.sin(dist * 0.08 - _syntheticPhase) + 1.0) * 0.5;

        // Base luminance pattern
        final baseLum = (wave * 200 + (y / height) * 55).clamp(0, 255).toInt();
        image.setPixelRgb(x, y, baseLum, baseLum, baseLum);
      }
    }

    StreamManager.instance.onFrameAvailable(image);
  }

  Future<void> dispose() async {
    _syntheticTimer?.cancel();
    if (_controller != null) {
      await _controller!.stopImageStream().catchError((_) {});
      await _controller!.dispose();
      _controller = null;
    }
    isCameraInitialized.value = false;
  }
}
